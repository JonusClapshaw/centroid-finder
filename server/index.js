const express = require("express");
const path = require("path");
const fs = require("fs");
const { execFile } = require("child_process");

/*
  Flow Map (request -> internal calls -> response)

  1) Shared middleware
    - express.json() parses JSON request bodies.
    - CORS middleware adds browser headers and returns 204 for OPTIONS preflight.

  2) Processing route: POST /process/run (raw/debug payload)
    req.body
     -> resolveProcessRequest(body)
     -> runProcessorAndReadCsv(config)
       -> execFileAsync("java", ["-jar", ...])
       -> fs.readFileSync(outputCsv)
       -> parseCsvRows(csvText)
     -> res.json({ status, paths, rowCount, rows, stdout, stderr })

  3) Processing route: POST /api/process (frontend payload)
    req.body
     -> resolveProcessRequest(body)
     -> runProcessorAndReadCsv(config)
       -> execFileAsync(...)
       -> fs.readFileSync(...)
       -> parseCsvRows(...)
     -> summarize rows (foundCount/missingCount)
     -> res.json({ success, data: { paths, summary, rows } })

  4) Static/discovery routes
    - GET /health and /api/health return server status.
    - GET /videos and /api/videos return discovered video catalog from processor/sampleInput.
    - GET /results, /api/results, /results/:videoId, /api/results/:videoId return mock result data.
    - GET /api/download/:jobId returns generated CSV as an attachment for a completed job.
    - GET /mock/results returns a convenience mock payload.

  5) Startup behavior
    - When run directly: app.listen(port).
    - When imported by tests: exports app without opening a network port.
*/

const app = express();
const port = process.env.PORT || 3000;
const repoRoot = path.resolve(__dirname, "..");
const jarPath = path.join(repoRoot, "processor", "target", "videoprocessor.jar");
const corsOrigin = process.env.CORS_ORIGIN || "*";
const FIXED_SAMPLE_RATE_HZ = 1;
const FRAME_INDEX_FALLBACK_FPS = 50;

app.use(express.json());
// Global middleware: allows browser-based frontend calls and short-circuits OPTIONS preflight.
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", corsOrigin);
  res.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.sendStatus(204);
    return;
  }

  next();
});

const videoInputDir = path.join(repoRoot, "processor", "sampleInput");
const VIDEO_EXTENSIONS = new Set([".mp4", ".mov", ".avi", ".webm"]);

const mockRows = [
  { timestamp: 0.0, x: 200, y: 436 },
  { timestamp: 1.0, x: 193, y: 454 },
  { timestamp: 2.0, x: 186, y: 452 },
  { timestamp: 3.0, x: 170, y: 462 },
];

const mockResults = {
  "ensantina.mp4": {
    videoId: "ensantina.mp4",
    rowCount: mockRows.length,
    rows: mockRows,
  },
  "demo.mp4": {
    videoId: "demo.mp4",
    rowCount: 3,
    rows: [
      { timestamp: 0.0, x: 100, y: 220 },
      { timestamp: 1.0, x: 101, y: 221 },
      { timestamp: 2.0, x: -1, y: -1 },
    ],
  },
};

// In-memory result cache keyed by jobId so frontend polling can fetch process output.
const jobResults = new Map();
// Runtime estimates per input path (milliseconds) used to compute progress percentages.
const processingEstimatesMs = new Map();
const DEFAULT_ESTIMATE_MS = 180000;

function createJobId() {
  return `job-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

function getEstimateMs(inputPath) {
  return processingEstimatesMs.get(inputPath) || DEFAULT_ESTIMATE_MS;
}

function updateEstimateMs(inputPath, actualDurationMs) {
  const previous = getEstimateMs(inputPath);
  const next = Math.round(previous * 0.7 + actualDurationMs * 0.3);
  processingEstimatesMs.set(inputPath, Math.max(10000, next));
}

function discoverVideos() {
  try {
    const files = fs.readdirSync(videoInputDir, { withFileTypes: true });
    return files
      .filter((entry) => entry.isFile())
      .map((entry) => entry.name)
      .filter((name) => VIDEO_EXTENSIONS.has(path.extname(name).toLowerCase()))
      .sort((a, b) => a.localeCompare(b))
      .map((name) => ({
        id: name,
        name,
        durationSeconds: 0,
      }));
  } catch {
    return [];
  }
}

function findThumbnailPath(filename) {
  const safeName = path.basename(filename || "");
  if (!safeName || safeName !== filename) {
    return null;
  }

  const parsed = path.parse(safeName);
  const candidates = [
    path.join(repoRoot, "processor", "sampleInput", `${parsed.name}.jpg`),
    path.join(repoRoot, "processor", "sampleInput", `${parsed.name}.jpeg`),
    path.join(repoRoot, "processor", "sampleInput", `${parsed.name}.png`),
    path.join(repoRoot, "processor", "sampleOutput", `${parsed.name}.png`),
    path.join(repoRoot, "processor", "sampleOutput", `${parsed.name}.jpg`),
  ];

  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function findVideoPath(filename) {
  const safeName = path.basename(filename || "");
  if (!safeName || safeName !== filename) {
    return null;
  }

  const candidates = [
    path.join(repoRoot, "processor", "sampleInput", safeName),
    path.join(repoRoot, "sampleInput", safeName),
  ];

  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function sendPlaceholderThumbnail(res, filename) {
  const label = path.parse(filename).name || "video";
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180">
      <rect width="320" height="180" fill="#1f2937"/>
      <circle cx="160" cy="90" r="34" fill="#f59e0b" opacity="0.9"/>
      <polygon points="150,72 150,108 182,90" fill="#111827"/>
      <text x="160" y="150" text-anchor="middle" fill="#f9fafb" font-family="Arial, sans-serif" font-size="18">${label}</text>
    </svg>
  `.trim();

  res.type("image/svg+xml").send(svg);
}

// Promise wrapper for child_process.execFile so route handlers can await Java execution.
// Called by: runProcessorAndReadCsv().
function execFileAsync(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    execFile(command, args, options, (error, stdout, stderr) => {
      if (error) {
        reject({ error, stdout, stderr });
        return;
      }
      resolve({ stdout, stderr });
    });
  });
}

// Parses processor CSV output into typed row objects.
// Called by: runProcessorAndReadCsv().
function parseCsvRows(csvText, sampleRateHz = FIXED_SAMPLE_RATE_HZ, options = {}) {
  const lines = csvText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  if (lines.length <= 1) {
    return [];
  }

  const parsedRows = lines.slice(1).map((line) => {
    const [timestamp, x, y] = line.split(",");
    return {
      timestamp: Number(timestamp),
      x: Number(x),
      y: Number(y),
    };
  });

  return downsampleRowsToRate(parsedRows, sampleRateHz, options);
}

function median(values) {
  if (!values.length) {
    return 0;
  }

  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 0) {
    return (sorted[middle - 1] + sorted[middle]) / 2;
  }

  return sorted[middle];
}

function looksLikeFrameIndexTimestamps(rows) {
  if (!Array.isArray(rows) || rows.length < 3) {
    return false;
  }

  const diffs = [];
  for (let i = 1; i < rows.length && diffs.length < 120; i++) {
    const previous = rows[i - 1]?.timestamp;
    const current = rows[i]?.timestamp;
    if (!Number.isFinite(previous) || !Number.isFinite(current)) {
      continue;
    }

    const diff = current - previous;
    if (diff > 0) {
      diffs.push(diff);
    }
  }

  if (!diffs.length) {
    return false;
  }

  const stepMedian = median(diffs);
  const lastTimestamp = rows[rows.length - 1]?.timestamp;
  const timestampsMirrorFrameCount = Number.isFinite(lastTimestamp)
    && lastTimestamp >= rows.length - 2;

  return stepMedian >= 0.9 && stepMedian <= 1.1 && timestampsMirrorFrameCount;
}

function normalizeFrameIndexRows(rows, videoDurationSeconds) {
  let sourceFramesPerSecond = FRAME_INDEX_FALLBACK_FPS;
  if (Number.isFinite(videoDurationSeconds) && videoDurationSeconds > 0) {
    sourceFramesPerSecond = rows.length / videoDurationSeconds;
  }

  if (!Number.isFinite(sourceFramesPerSecond) || sourceFramesPerSecond <= 0) {
    sourceFramesPerSecond = FRAME_INDEX_FALLBACK_FPS;
  }

  return rows.map((row, frameIndex) => ({
    timestamp: frameIndex / sourceFramesPerSecond,
    x: row.x,
    y: row.y,
  }));
}

function downsampleRowsToRate(rows, sampleRateHz = 1, options = {}) {
  const firstRowByBucket = new Map();
  const safeSampleRateHz = Number.isFinite(sampleRateHz) && sampleRateHz > 0
    ? sampleRateHz
    : 1;
  const candidateRows = looksLikeFrameIndexTimestamps(rows)
    ? normalizeFrameIndexRows(rows, options.videoDurationSeconds)
    : rows;

  for (const row of candidateRows || []) {
    if (!Number.isFinite(row?.timestamp)) {
      continue;
    }

    const timeBucket = Math.floor((row.timestamp * safeSampleRateHz) + 1e-9);
    if (!firstRowByBucket.has(timeBucket)) {
      firstRowByBucket.set(timeBucket, {
        timestamp: timeBucket / safeSampleRateHz,
        x: row.x,
        y: row.y,
      });
    }
  }

  return Array.from(firstRowByBucket.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([, row]) => row);
}

function buildCsvTextFromRows(rows) {
  const lines = ["timestamp,x,y"];

  for (const row of rows || []) {
    lines.push(`${Number(row.timestamp).toFixed(3)},${row.x},${row.y}`);
  }

  return lines.join("\n");
}

function computeAverageLocation(rows) {
  const detectedRows = (rows || []).filter(
    (row) => Number.isFinite(row?.x) && Number.isFinite(row?.y) && row.x >= 0 && row.y >= 0
  );

  if (detectedRows.length === 0) {
    return {
      averageX: null,
      averageY: null,
      sampleCount: 0,
    };
  }

  const totals = detectedRows.reduce(
    (acc, row) => ({ x: acc.x + row.x, y: acc.y + row.y }),
    { x: 0, y: 0 }
  );

  return {
    averageX: totals.x / detectedRows.length,
    averageY: totals.y / detectedRows.length,
    sampleCount: detectedRows.length,
  };
}

function buildDownloadCsvWithSummary(csvText, rows) {
  const averageLocation = computeAverageLocation(rows);

  if (!averageLocation.sampleCount) {
    return [
      "# Average centroid placement: unavailable (no detections found)",
      csvText,
    ].join("\n");
  }

  return [
    `# Average centroid placement (based on ${averageLocation.sampleCount} detections): x=${averageLocation.averageX.toFixed(2)}, y=${averageLocation.averageY.toFixed(2)}`,
    csvText,
  ].join("\n");
}

// Validates request body and resolves absolute file paths.
// Called by: POST /process/run and POST /api/process.
// Throws errors with HTTP-friendly status codes for route-level error handling.
function resolveProcessRequest(body = {}) {
  const {
    videoPath,
    inputPath = "processor/sampleInput/ensantina.mp4",
    outputCsv = "output.csv",
    videoDurationSeconds,
    targetColor,
    threshold,
  } = body;

  if (!targetColor || typeof targetColor !== "string") {
    const error = new Error("targetColor is required and must be a string.");
    error.status = 400;
    throw error;
  }

  if (!Number.isInteger(threshold)) {
    const error = new Error("threshold is required and must be an integer.");
    error.status = 400;
    throw error;
  }

  if (videoDurationSeconds != null) {
    if (!Number.isFinite(videoDurationSeconds) || videoDurationSeconds <= 0) {
      const error = new Error("videoDurationSeconds must be a positive number when provided.");
      error.status = 400;
      throw error;
    }
  }

  if (!fs.existsSync(jarPath)) {
    const error = new Error(`Processor jar not found. Expected: ${jarPath}`);
    error.status = 500;
    throw error;
  }

  const selectedInputPath = videoPath || inputPath;
  const resolvedInputPath = path.isAbsolute(selectedInputPath)
    ? selectedInputPath
    : path.join(repoRoot, selectedInputPath);

  if (!fs.existsSync(resolvedInputPath)) {
    const error = new Error(`Input video not found: ${resolvedInputPath}`);
    error.status = 400;
    throw error;
  }

  const resolvedOutputCsv = path.isAbsolute(outputCsv)
    ? outputCsv
    : path.join(repoRoot, outputCsv);

  return {
    resolvedInputPath,
    resolvedOutputCsv,
    targetColor,
    threshold,
    videoDurationSeconds,
  };
}

// Runs the Java processor jar and returns parsed CSV rows.
// Called by: POST /process/run and POST /api/process after resolveProcessRequest().
// Internal call sequence: execFileAsync() -> fs.readFileSync() -> parseCsvRows().
async function runProcessorAndReadCsv(config) {
  const { resolvedInputPath, resolvedOutputCsv, targetColor, threshold, videoDurationSeconds } = config;
  const { stdout, stderr } = await execFileAsync(
    "java",
    ["-jar", jarPath, resolvedInputPath, resolvedOutputCsv, targetColor, String(threshold)],
    { cwd: repoRoot, maxBuffer: 1024 * 1024 * 10 }
  );

  const csvText = fs.readFileSync(resolvedOutputCsv, "utf8");
  const rows = parseCsvRows(csvText, FIXED_SAMPLE_RATE_HZ, { videoDurationSeconds });
  return { stdout, stderr, rows };
}

function startBackgroundProcessing(jobId, config) {
  const startedAt = Date.now();
  const estimatedMs = getEstimateMs(config.resolvedInputPath);

  const progressTimer = setInterval(() => {
    const job = jobResults.get(jobId);
    if (!job || job.status !== "processing") {
      clearInterval(progressTimer);
      return;
    }

    const elapsedMs = Date.now() - startedAt;
    // Cap at 95% until completion so users can distinguish in-progress vs done.
    const progressPercent = Math.min(95, Math.floor((elapsedMs / estimatedMs) * 95));
    job.progressPercent = Math.max(job.progressPercent || 0, progressPercent);
    job.elapsedMs = elapsedMs;
  }, 1000);

  (async () => {
    try {
      const { rows } = await runProcessorAndReadCsv(config);
      const missingCount = rows.filter((row) => row.x === -1 && row.y === -1).length;
      const averageLocation = computeAverageLocation(rows);
      const finishedAt = Date.now();
      const durationMs = finishedAt - startedAt;

      updateEstimateMs(config.resolvedInputPath, durationMs);

      jobResults.set(jobId, {
        jobId,
        status: "completed",
        progressPercent: 100,
        startedAt,
        finishedAt,
        elapsedMs: durationMs,
        data: {
          inputVideoPath: config.resolvedInputPath,
          outputCsvPath: config.resolvedOutputCsv,
          summary: {
            rowCount: rows.length,
            foundCount: rows.length - missingCount,
            missingCount,
            sampleRateHz: FIXED_SAMPLE_RATE_HZ,
            averageX: averageLocation.averageX == null ? null : Number(averageLocation.averageX.toFixed(2)),
            averageY: averageLocation.averageY == null ? null : Number(averageLocation.averageY.toFixed(2)),
            averageLocation: averageLocation.sampleCount
              ? {
                  x: Number(averageLocation.averageX.toFixed(2)),
                  y: Number(averageLocation.averageY.toFixed(2)),
                  sampleCount: averageLocation.sampleCount,
                }
              : null,
          },
          rows,
        },
      });
    } catch (caughtError) {
      const error = caughtError.error || caughtError;
      const stderr = caughtError.stderr;
      const failedAt = Date.now();

      jobResults.set(jobId, {
        jobId,
        status: "failed",
        progressPercent: 100,
        startedAt,
        finishedAt: failedAt,
        elapsedMs: failedAt - startedAt,
        error: stderr || error.message,
      });
    } finally {
      clearInterval(progressTimer);
    }
  })();
}

// Liveness route for quick infrastructure checks.
app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

// API namespace alias for frontend consistency.
app.get("/api/health", (_req, res) => {
  res.json({ status: "ok" });
});

// Mock catalog route used by UI for video selection.
app.get("/videos", (_req, res) => {
  res.json({ videos: discoverVideos() });
});

// API namespace alias for frontend consistency.
app.get("/api/videos", (_req, res) => {
  res.json({ videos: discoverVideos() });
});

app.get("/video/:filename", (req, res) => {
  const videoPath = findVideoPath(req.params.filename);
  if (!videoPath) {
    res.status(404).json({ error: "Video not found." });
    return;
  }

  res.sendFile(videoPath);
});

app.get("/api/video/:filename", (req, res) => {
  const videoPath = findVideoPath(req.params.filename);
  if (!videoPath) {
    res.status(404).json({ error: "Video not found." });
    return;
  }

  res.sendFile(videoPath);
});

app.get("/videos/:filename", (req, res) => {
  const videoPath = findVideoPath(req.params.filename);
  if (!videoPath) {
    res.status(404).json({ error: "Video not found." });
    return;
  }

  res.sendFile(videoPath);
});

app.get("/api/videos/:filename", (req, res) => {
  const videoPath = findVideoPath(req.params.filename);
  if (!videoPath) {
    res.status(404).json({ error: "Video not found." });
    return;
  }

  res.sendFile(videoPath);
});

app.get("/thumbnail/:filename", (req, res) => {
  const thumbnailPath = findThumbnailPath(req.params.filename);
  if (path.basename(req.params.filename) !== req.params.filename) {
    res.status(404).json({ error: "Thumbnail not found." });
    return;
  }

  if (!thumbnailPath) {
    sendPlaceholderThumbnail(res, req.params.filename);
    return;
  }

  res.sendFile(thumbnailPath);
});

app.get("/api/thumbnail/:filename", (req, res) => {
  const thumbnailPath = findThumbnailPath(req.params.filename);
  if (path.basename(req.params.filename) !== req.params.filename) {
    res.status(404).json({ error: "Thumbnail not found." });
    return;
  }

  if (!thumbnailPath) {
    sendPlaceholderThumbnail(res, req.params.filename);
    return;
  }

  res.sendFile(thumbnailPath);
});

// Mock aggregate results route.
app.get("/results", (_req, res) => {
  res.json({
    results: Object.values(mockResults),
  });
});

// API namespace alias for frontend consistency.
app.get("/api/results", (_req, res) => {
  const { jobId } = _req.query;

  if (jobId) {
    const job = jobResults.get(jobId);
    if (!job) {
      res.status(404).json({ success: false, error: "Job result not found." });
      return;
    }

    res.json({ success: true, ...job });
    return;
  }

  res.json({
    results: Object.values(mockResults),
  });
});

// Mock single-result route by video id.
app.get("/results/:videoId", (req, res) => {
  const result = mockResults[req.params.videoId];
  if (!result) {
    res.status(404).json({ error: "Result not found for video." });
    return;
  }
  res.json(result);
});

// API namespace alias for frontend consistency.
app.get("/api/results/:videoId", (req, res) => {
  const result = mockResults[req.params.videoId];
  if (!result) {
    res.status(404).json({ error: "Result not found for video." });
    return;
  }
  res.json(result);
});

// Job CSV download route for frontend download button.
// Uses jobId from completed /api/process results and returns CSV as attachment.
app.get("/api/download/:jobId", (req, res) => {
  const { jobId } = req.params;
  const job = jobResults.get(jobId);

  if (!job) {
    res.status(404).json({ success: false, error: "Job not found." });
    return;
  }

  const csvPath = job?.data?.outputCsvPath;
  if (!csvPath || !fs.existsSync(csvPath)) {
    res.status(404).json({ success: false, error: "CSV not found." });
    return;
  }

  try {
    const csvText = fs.readFileSync(csvPath, "utf8");
    const normalizedRows = Array.isArray(job?.data?.rows)
      ? job.data.rows
      : parseCsvRows(csvText);
    const normalizedCsvText = buildCsvTextFromRows(normalizedRows);
    const csvWithSummary = buildDownloadCsvWithSummary(normalizedCsvText, normalizedRows);
    res
      .status(200)
      .set("Content-Disposition", `attachment; filename="${path.basename(csvPath)}"`)
      .type("text/csv")
      .send(csvWithSummary);
  } catch (error) {
    res.status(500).json({ success: false, error: error.message || "Failed to read CSV." });
  }
});

// Convenience mock route for quick UI/testing previews.
app.get("/mock/results", (_req, res) => {
  res.json({ mock: true, ...mockResults["ensantina.mp4"] });
});

// Raw processing route (debug-friendly).
// Flow: resolveProcessRequest() -> runProcessorAndReadCsv() -> return rows + stdout/stderr.
app.post("/process/run", async (req, res) => {
  try {
    const config = resolveProcessRequest(req.body);
    const { stdout, stderr, rows } = await runProcessorAndReadCsv(config);

    res.json({
      status: "done",
      inputVideoPath: config.resolvedInputPath,
      outputCsvPath: config.resolvedOutputCsv,
      rowCount: rows.length,
      rows,
      stdout,
      stderr,
    });
  } catch (caughtError) {
    const error = caughtError.error || caughtError;
    const stderr = caughtError.stderr;
    res.status(error.status || 500).json({
      error: stderr || error.message,
      details: stderr || error.message,
    });
  }
});

// Frontend-focused processing route.
// Flow: resolveProcessRequest() -> runProcessorAndReadCsv() -> compute summary -> return UI-ready payload.
app.post("/api/process", async (req, res) => {
  try {
    const config = resolveProcessRequest(req.body);
    const jobId = createJobId();

    jobResults.set(jobId, {
      jobId,
      status: "processing",
      progressPercent: 0,
      startedAt: Date.now(),
    });

    startBackgroundProcessing(jobId, config);

    res.status(202).json({
      success: true,
      jobId,
      status: "processing",
      progressPercent: 0,
    });
  } catch (caughtError) {
    const error = caughtError.error || caughtError;
    const stderr = caughtError.stderr;
    res.status(error.status || 500).json({
      success: false,
      error: stderr || error.message,
    });
  }
});

// Starts the HTTP server only when running this file directly.
// During tests, the app is exported without listening so supertest can mount it in-memory.
if (require.main === module) {
  app.listen(port, () => {
    console.log(`Server listening on port ${port}`);
  });
}

module.exports = app;
