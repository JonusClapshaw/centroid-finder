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

app.post("/process/run", (req, res) => {
  const {
    videoPath,
    inputPath = "processor/sampleInput/ensantina.mp4",
    outputCsv = "output.csv",
    targetColor,
    threshold,
  } = req.body || {};

  if (!targetColor || typeof targetColor !== "string") {
    res.status(400).json({ error: "targetColor is required and must be a string." });
    return;
  }

  if (!Number.isInteger(threshold)) {
    res.status(400).json({ error: "threshold is required and must be an integer." });
    return;
  }

  if (!fs.existsSync(jarPath)) {
    res.status(500).json({
      error: "Processor jar not found.",
      details: `Expected: ${jarPath}`,
    });
    return;
  }

  const selectedInputPath = videoPath || inputPath;

  const resolvedInputPath = path.isAbsolute(selectedInputPath)
    ? selectedInputPath
    : path.join(repoRoot, selectedInputPath);

  if (!fs.existsSync(resolvedInputPath)) {
    res.status(400).json({ error: `Input video not found: ${resolvedInputPath}` });
    return;
  }

  const resolvedOutputCsv = path.isAbsolute(outputCsv)
    ? outputCsv
    : path.join(repoRoot, outputCsv);

  execFile(
    "java",
    ["-jar", jarPath, resolvedInputPath, resolvedOutputCsv, targetColor, String(threshold)],
    { cwd: repoRoot, maxBuffer: 1024 * 1024 * 10 },
    (error, stdout, stderr) => {
      if (error) {
        res.status(500).json({
          error: "Failed to execute processor jar.",
          details: stderr || error.message,
        });
        return;
      }

      res.json({
        status: "done",
        inputVideoPath: resolvedInputPath,
        outputCsvPath: resolvedOutputCsv,
        stdout,
        stderr,
      });
    }
  );
});

if (require.main === module) {
  app.listen(port, () => {
    console.log(`Server listening on port ${port}`);
  });
}

module.exports = app;
