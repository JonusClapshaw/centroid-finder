const baseUrl = process.env.BASE_URL || "http://localhost:3000";

async function readJson(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Expected JSON but got: ${text}`);
  }
}

async function main() {
  const healthResponse = await fetch(`${baseUrl}/health`);
  if (!healthResponse.ok) {
    throw new Error(`GET /health failed with status ${healthResponse.status}`);
  }
  const health = await readJson(healthResponse);

  const processResponse = await fetch(`${baseUrl}/api/process`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      videoPath: "processor/sampleInput/ensantina.mp4",
      outputCsv: "output.csv",
      targetColor: "450907",
      threshold: 25,
    }),
  });

  if (!processResponse.ok) {
    const details = await processResponse.text();
    throw new Error(`POST /api/process failed with status ${processResponse.status}: ${details}`);
  }

  const processResult = await readJson(processResponse);

  if (!processResult.jobId) {
    throw new Error("POST /api/process did not return a jobId.");
  }

  let finalResult = null;
  for (let i = 0; i < 20; i++) {
    const pollResponse = await fetch(`${baseUrl}/api/results?jobId=${processResult.jobId}`);
    if (!pollResponse.ok) {
      const details = await pollResponse.text();
      throw new Error(`GET /api/results failed with status ${pollResponse.status}: ${details}`);
    }

    const polled = await readJson(pollResponse);
    if (polled.status !== "processing") {
      finalResult = polled;
      break;
    }

    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  if (!finalResult) {
    throw new Error("Timed out waiting for job completion.");
  }

  if (finalResult.status !== "completed") {
    throw new Error(`Job failed: ${finalResult.error || "unknown error"}`);
  }

  console.log("Health OK:", health);
  console.log("Process OK:", {
    success: finalResult.success,
    inputVideoPath: finalResult.data?.inputVideoPath,
    outputCsvPath: finalResult.data?.outputCsvPath,
    rowCount: finalResult.data?.summary?.rowCount,
  });
}

main().catch((error) => {
  console.error("Smoke test failed:", error.message);
  process.exit(1);
});
