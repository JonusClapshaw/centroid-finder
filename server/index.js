const express = require("express");
const path = require("path");
const fs = require("fs");
const { execFile } = require("child_process");

const app = express();
const port = process.env.PORT || 3000;
const repoRoot = path.resolve(__dirname, "..");
const jarPath = path.join(repoRoot, "processor", "target", "videoprocessor.jar");

app.use(express.json());

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

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.get("/videos", (_req, res) => {
  res.json({ videos: mockVideos });
});

app.get("/results", (_req, res) => {
  res.json({
    results: Object.values(mockResults),
  });
});

app.get("/results/:videoId", (req, res) => {
  const result = mockResults[req.params.videoId];
  if (!result) {
    res.status(404).json({ error: "Result not found for video." });
    return;
  }
  res.json(result);
});

app.get("/mock/results", (_req, res) => {
  res.json({ mock: true, ...mockResults["ensantina.mp4"] });
});

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

if (require.main === module) {
  app.listen(port, () => {
    console.log(`Server listening on port ${port}`);
  });
}

module.exports = app;
