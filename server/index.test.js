const request = require("supertest");

jest.mock("fs", () => {
  const actualFs = jest.requireActual("fs");
  return {
    ...actualFs,
    existsSync: jest.fn(),
    readFileSync: jest.fn(),
  };
});

jest.mock("child_process", () => ({
  execFile: jest.fn(),
}));

const fs = require("fs");
const { execFile } = require("child_process");
const app = require("./index");

// These tests verify browser-facing behavior around API namespace aliases and CORS headers.
describe("Frontend API compatibility", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Confirms middleware runs before route handler and sets CORS headers.
  test("adds CORS headers on health route", async () => {
    const response = await request(app).get("/api/health");

    expect(response.status).toBe(200);
    expect(response.headers["access-control-allow-origin"]).toBe("*");
    expect(response.body).toEqual({ status: "ok" });
  });

  test("supports API namespace aliases for videos", async () => {
    const response = await request(app).get("/api/videos");

    expect(response.status).toBe(200);
    expect(Array.isArray(response.body.videos)).toBe(true);
    expect(response.body.videos).toEqual([
      expect.objectContaining({ id: "ensantina.mp4", name: "ensantina.mp4" }),
    ]);
  });

  test("serves a real video file for api video route", async () => {
    fs.existsSync.mockImplementation((filePath) => String(filePath).includes("ensantina.mp4"));

    const response = await request(app).get("/api/video/ensantina.mp4");

    expect(response.status).toBe(200);
    expect(response.headers["content-type"]).toMatch(/video\//);
  });

  test("returns 404 for missing video file", async () => {
    fs.existsSync.mockReturnValue(false);

    const response = await request(app).get("/api/video/demo.mp4");

    expect(response.status).toBe(404);
    expect(response.body.error).toMatch(/Video not found/i);
  });

  test("rejects unsafe video paths", async () => {
    const response = await request(app).get("/api/video/%2E%2E%2Fsecret.mp4");

    expect(response.status).toBe(404);
    expect(response.body.error).toMatch(/Video not found/i);
  });

  test("serves a thumbnail placeholder for known video names", async () => {
    fs.existsSync.mockReturnValue(false);

    const response = await request(app).get("/api/thumbnail/ensantina.mp4");

    expect(response.status).toBe(200);
    expect(response.headers["content-type"]).toMatch(/image\/svg\+xml/);
  });

  test("rejects unsafe thumbnail paths", async () => {
    const response = await request(app).get("/api/thumbnail/%2E%2E%2Fsecret.txt");

    expect(response.status).toBe(404);
    expect(response.body.error).toMatch(/Thumbnail not found/i);
  });
});

// These tests cover the raw processing route call chain:
// resolveProcessRequest() -> runProcessorAndReadCsv() -> execFileAsync() -> parseCsvRows().
describe("POST /process/run", () => {
  // Reset mock call history so each test validates one branch in isolation.
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Validation branch: resolveProcessRequest() should reject early before any file/process I/O.
  test("returns 400 when targetColor is missing", async () => {
    const response = await request(app)
      .post("/process/run")
      .send({ threshold: 25 });

    expect(response.status).toBe(400);
    expect(response.body.error).toMatch(/targetColor is required/i);
  });

  test("returns 400 when threshold is not an integer", async () => {
    const response = await request(app)
      .post("/process/run")
      .send({ targetColor: "450907", threshold: "25" });

    expect(response.status).toBe(400);
    expect(response.body.error).toMatch(/threshold is required and must be an integer/i);
  });

  test("returns 500 when processor jar is missing", async () => {
    fs.existsSync.mockReturnValue(false);

    const response = await request(app)
      .post("/process/run")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.error).toMatch(/Processor jar not found/i);
    expect(execFile).not.toHaveBeenCalled();
  });

  test("returns 400 when input video is missing", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(false);

    const response = await request(app)
      .post("/process/run")
      .send({ targetColor: "450907", threshold: 25, inputPath: "processor/sampleInput/missing.mp4" });

    expect(response.status).toBe(400);
    expect(response.body.error).toMatch(/Input video not found/i);
    expect(execFile).not.toHaveBeenCalled();
  });

  // Execution failure branch: java process started but exits with error/stderr.
  test("returns 500 when java execution fails", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(new Error("java failed"), "", "simulated stderr");
    });

    const response = await request(app)
      .post("/process/run")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.error).toMatch(/simulated stderr/i);
    expect(response.body.details).toMatch(/simulated stderr/i);
  });

  // Happy path branch: java succeeds, CSV is parsed, and rows are returned.
  test("returns 200 when java execution succeeds", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    fs.readFileSync.mockReturnValue(
      [
        "timestamp,x,y",
        "0.000,200,436",
        "1.000,193,454",
      ].join("\n")
    );

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    const response = await request(app)
      .post("/process/run")
      .send({
        targetColor: "450907",
        threshold: 25,
        outputCsv: "output.csv",
      });

    expect(response.status).toBe(200);
    expect(response.body.status).toBe("done");
    expect(response.body.outputCsvPath).toContain("output.csv");
    expect(response.body.rowCount).toBe(2);
    expect(response.body.rows[0]).toEqual({ timestamp: 0, x: 200, y: 436 });
    expect(response.body.stdout).toBe("simulated stdout");
    expect(execFile).toHaveBeenCalledTimes(1);
  });

  // Verifies request input is propagated into java args used by child_process.execFile.
  test("passes videoPath to Java program", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    fs.readFileSync.mockReturnValue(
      [
        "timestamp,x,y",
        "0.000,200,436",
      ].join("\n")
    );

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    const response = await request(app)
      .post("/process/run")
      .send({
        videoPath: "processor/sampleInput/ensantina.mp4",
        targetColor: "450907",
        threshold: 25,
      });

    expect(response.status).toBe(200);
    expect(execFile).toHaveBeenCalledTimes(1);
    const args = execFile.mock.calls[0][1];
    expect(args[2]).toContain("processor");
    expect(args[2]).toContain("ensantina.mp4");
    expect(response.body.inputVideoPath).toContain("ensantina.mp4");
  });

  test("returns 500 when CSV cannot be read after java succeeds", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    fs.readFileSync.mockImplementation(() => {
      throw new Error("ENOENT: no such file or directory");
    });

    const response = await request(app)
      .post("/process/run")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.error).toMatch(/ENOENT/i);
    expect(response.body.details).toMatch(/ENOENT/i);
  });
});

// These tests cover the frontend-oriented route, which uses the same backend pipeline
// but returns a UI-friendly payload: { success, data: { summary, rows, ... } }.
describe("POST /api/process", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Happy path with one detected centroid and one missing centroid in summary counts.
  test("returns frontend-shaped data response", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    fs.readFileSync.mockReturnValue(
      [
        "timestamp,x,y",
        "0.000,200,436",
        "1.000,-1,-1",
      ].join("\n")
    );

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    const response = await request(app)
      .post("/api/process")
      .send({
        targetColor: "450907",
        threshold: 25,
        outputCsv: "output.csv",
      });

    expect(response.status).toBe(200);
    expect(response.body.success).toBe(true);
    expect(response.body.jobId).toMatch(/^job-/);
    expect(response.body.status).toBe("completed");
    expect(response.body.data.summary.rowCount).toBe(2);
    expect(response.body.data.summary.foundCount).toBe(1);
    expect(response.body.data.summary.missingCount).toBe(1);
    expect(response.body.data.rows[1]).toEqual({ timestamp: 1, x: -1, y: -1 });
  });

  test("supports polling result by jobId via /api/results?jobId=...", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    fs.readFileSync.mockReturnValue(
      [
        "timestamp,x,y",
        "0.000,200,436",
      ].join("\n")
    );

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    const processResponse = await request(app)
      .post("/api/process")
      .send({
        targetColor: "450907",
        threshold: 25,
      });

    expect(processResponse.status).toBe(200);
    expect(processResponse.body.jobId).toBeTruthy();

    const pollResponse = await request(app)
      .get(`/api/results?jobId=${processResponse.body.jobId}`);

    expect(pollResponse.status).toBe(200);
    expect(pollResponse.body.success).toBe(true);
    expect(pollResponse.body.jobId).toBe(processResponse.body.jobId);
    expect(pollResponse.body.status).toBe("completed");
    expect(pollResponse.body.data.summary.rowCount).toBe(1);
  });

  test("returns 404 when polling unknown jobId", async () => {
    const response = await request(app)
      .get("/api/results?jobId=job-does-not-exist");

    expect(response.status).toBe(404);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/Job result not found/i);
  });

  // Validation error branch with frontend response shape.
  test("returns 400-style validation error for missing targetColor", async () => {
    const response = await request(app)
      .post("/api/process")
      .send({ threshold: 25 });

    expect(response.status).toBe(400);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/targetColor is required/i);
  });

  test("returns 500 when processor jar is missing", async () => {
    fs.existsSync.mockReturnValue(false);

    const response = await request(app)
      .post("/api/process")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/Processor jar not found/i);
    expect(execFile).not.toHaveBeenCalled();
  });

  test("returns 500 when java execution fails", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(new Error("java failed"), "", "simulated stderr");
    });

    const response = await request(app)
      .post("/api/process")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/simulated stderr/i);
  });

  test("returns 500 when CSV cannot be read after java succeeds", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

    execFile.mockImplementation((cmd, args, opts, cb) => {
      cb(null, "simulated stdout", "");
    });

    fs.readFileSync.mockImplementation(() => {
      throw new Error("EACCES: permission denied");
    });

    const response = await request(app)
      .post("/api/process")
      .send({ targetColor: "450907", threshold: 25 });

    expect(response.status).toBe(500);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/EACCES/i);
  });
});
