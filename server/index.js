const express = require("express");

const app = express();
const port = process.env.PORT || 3000;

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.get("/mock/results", (_req, res) => {
  res.json({
    mock: true,
    rowCount: 4,
    rows: [
      { timestamp: 0.0, x: 200, y: 436 },
      { timestamp: 1.0, x: 193, y: 454 },
      { timestamp: 2.0, x: 186, y: 452 },
      { timestamp: 3.0, x: 170, y: 462 },
    ],
  });
});

app.listen(port, () => {
  console.log(`Server listening on port ${port}`);
});
