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

  console.log("Health OK:", health);
  console.log("Process OK:", {
    success: processResult.success,
    inputVideoPath: processResult.data?.inputVideoPath,
    outputCsvPath: processResult.data?.outputCsvPath,
    rowCount: processResult.data?.summary?.rowCount,
  });
}

main().catch((error) => {
  console.error("Smoke test failed:", error.message);
  process.exit(1);
});
