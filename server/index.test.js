const request = require("supertest");

jest.mock("fs", () => ({
  existsSync: jest.fn(),
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
    expect(response.body.error).toMatch(/Failed to execute processor jar/i);
    expect(response.body.details).toMatch(/simulated stderr/i);
  });

  test("returns 200 when java execution succeeds", async () => {
    fs.existsSync
      .mockReturnValueOnce(true)
      .mockReturnValueOnce(true);

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
    expect(response.body.stdout).toBe("simulated stdout");
    expect(execFile).toHaveBeenCalledTimes(1);
  });
});
