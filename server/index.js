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

  4) Mock/static routes
    - GET /health and /api/health return server status.
    - GET /videos and /api/videos return mock video catalog.
    - GET /results, /api/results, /results/:videoId, /api/results/:videoId return mock result data.
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

const mockVideos = [
  { id: "ensantina.mp4", name: "ensantina.mp4", durationSeconds: 480 },
  { id: "demo.mp4", name: "demo.mp4", durationSeconds: 120 },
];

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
function parseCsvRows(csvText) {
  const lines = csvText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  if (lines.length <= 1) {
    return [];
  }

  return lines.slice(1).map((line) => {
    const [timestamp, x, y] = line.split(",");
    return {
      timestamp: Number(timestamp),
      x: Number(x),
      y: Number(y),
    };
  });
}

// Validates request body and resolves absolute file paths.
// Called by: POST /process/run and POST /api/process.
// Throws errors with HTTP-friendly status codes for route-level error handling.
function resolveProcessRequest(body = {}) {
  const {
    videoPath,
    inputPath = "processor/sampleInput/ensantina.mp4",
    outputCsv = "output.csv",
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

  return { resolvedInputPath, resolvedOutputCsv, targetColor, threshold };
}

// Runs the Java processor jar and returns parsed CSV rows.
// Called by: POST /process/run and POST /api/process after resolveProcessRequest().
// Internal call sequence: execFileAsync() -> fs.readFileSync() -> parseCsvRows().
async function runProcessorAndReadCsv(config) {
  const { resolvedInputPath, resolvedOutputCsv, targetColor, threshold } = config;
  const { stdout, stderr } = await execFileAsync(
    "java",
    ["-jar", jarPath, resolvedInputPath, resolvedOutputCsv, targetColor, String(threshold)],
    { cwd: repoRoot, maxBuffer: 1024 * 1024 * 10 }
  );

  const csvText = fs.readFileSync(resolvedOutputCsv, "utf8");
  const rows = parseCsvRows(csvText);
  return { stdout, stderr, rows };
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
  res.json({ videos: mockVideos });
});

// API namespace alias for frontend consistency.
app.get("/api/videos", (_req, res) => {
  res.json({ videos: mockVideos });
});

// Mock aggregate results route.
app.get("/results", (_req, res) => {
  res.json({
    results: Object.values(mockResults),
  });
});

// API namespace alias for frontend consistency.
app.get("/api/results", (_req, res) => {
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
    const { rows } = await runProcessorAndReadCsv(config);
    const missingCount = rows.filter((row) => row.x === -1 && row.y === -1).length;

    res.json({
      success: true,
      data: {
        inputVideoPath: config.resolvedInputPath,
        outputCsvPath: config.resolvedOutputCsv,
        summary: {
          rowCount: rows.length,
          foundCount: rows.length - missingCount,
          missingCount,
        },
        rows,
      },
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
