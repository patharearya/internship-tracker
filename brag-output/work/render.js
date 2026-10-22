// node render.js stills 0.8 2.5 ...   -> stills/t-<s>.png
// node render.js frames               -> frames/f-00000.jpg ... (30 fps)
const puppeteer = require("puppeteer-core");
const fs = require("fs"), path = require("path");
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const DUR = 21.5, FPS = 30;

(async () => {
  const [mode, ...times] = process.argv.slice(2);
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: true, args: ["--allow-file-access-from-files", "--force-color-profile=srgb"] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });
  await page.goto("file:///" + path.resolve(__dirname, "composition.html").replace(/\\/g, "/"), { waitUntil: "networkidle0" });
  await page.evaluate(() => window.ready);
  const shot = async (t, file, type) => { await page.evaluate(t => render(t), t); await page.screenshot({ path: file, type, quality: type === "jpeg" ? 95 : undefined }); };
  if (mode === "stills") {
    fs.mkdirSync(path.join(__dirname, "stills"), { recursive: true });
    for (const t of times) await shot(+t, path.join(__dirname, "stills", `t-${t}.png`), "png");
  } else {
    const dir = path.join(__dirname, "frames"); fs.mkdirSync(dir, { recursive: true });
    const n = Math.round(DUR * FPS);
    for (let i = 0; i < n; i++) {
      await shot(i / FPS, path.join(dir, `f-${String(i).padStart(5, "0")}.jpg`), "jpeg");
      if (i % 60 === 0) console.log(`frame ${i}/${n}`);
    }
  }
  await browser.close();
})();
