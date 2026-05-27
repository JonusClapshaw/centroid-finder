const request = require("supertest");

jest.mock("fs", () => ({
  existsSync: jest.fn(),
  readFileSync: jest.fn(),
}));

jest.mock("child_process", () => ({
  execFile: jest.fn(),
}));

const fs = require("fs");
const { execFile } = require("child_process");
const app = require("./index");

describe("POST /process/run", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

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
});

describe("POST /api/process", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

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
    expect(response.body.data.summary.rowCount).toBe(2);
    expect(response.body.data.summary.foundCount).toBe(1);
    expect(response.body.data.summary.missingCount).toBe(1);
    expect(response.body.data.rows[1]).toEqual({ timestamp: 1, x: -1, y: -1 });
  });

  test("returns 400-style validation error for missing targetColor", async () => {
    const response = await request(app)
      .post("/api/process")
      .send({ threshold: 25 });

    expect(response.status).toBe(400);
    expect(response.body.success).toBe(false);
    expect(response.body.error).toMatch(/targetColor is required/i);
  });
});
