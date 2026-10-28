/* Optional browser QA: NODE_PATH must include Playwright. Screenshots go to build/dashboard-qa. */
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const root = path.resolve(__dirname, "../src/main/resources/static");
const output = process.env.DASHBOARD_SCREENSHOTS || path.resolve(__dirname, "../build/dashboard-qa");
fs.mkdirSync(output, { recursive:true });
let snapshot = {serverTime:new Date().toISOString(),connection:"WAITING",machineStatus:"UNKNOWN",lastReceivedAt:null,totalSamples:0,points:[]};
const server = http.createServer((req,res)=>{
  const pathname = new URL(req.url,"http://localhost").pathname;
  if(pathname==="/api/dashboard"){res.setHeader("Content-Type","application/json");res.end(JSON.stringify(snapshot));return;}
  if(pathname==="/api/experiments"){res.setHeader("Content-Type","application/json");res.end("[]");return;}
  const files={"/":"index.html","/app.css":"app.css","/app.js":"app.js","/icon.svg":"icon.svg"};
  if(!files[pathname]){res.writeHead(404);res.end();return;}
  res.setHeader("Content-Type",pathname.endsWith(".css")?"text/css":pathname.endsWith(".js")?"application/javascript":pathname.endsWith(".svg")?"image/svg+xml":"text/html;charset=utf-8");
  res.end(fs.readFileSync(path.join(root,files[pathname])));
});
(async()=>{
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  const browser=await chromium.launch({channel:"chrome",headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1080},acceptDownloads:true});
  const page=await context.newPage(), errors=[];
  page.on("pageerror",error=>errors.push(error.message));
  const base="http://127.0.0.1:"+server.address().port;
  try{
    await page.goto(base);
    await page.waitForFunction(()=>document.querySelector("#connectionStatus").textContent==="수신 대기");
    assert.equal(await page.locator("#dataEmpty").isVisible(),true);
    snapshot={...snapshot,connection:"STALE",machineStatus:"AVAILABLE",lastReceivedAt:new Date(Date.now()-30000).toISOString()};
    await page.click("#refreshButton");
    await page.waitForFunction(()=>document.querySelector("#connectionStatus").textContent==="연결 확인 필요");
    assert.equal(await page.locator("#machineStatus").textContent(),"확인 필요");
    await page.click("#demoMode");
    await page.waitForFunction(()=>document.querySelector("#dataRows").children.length===10);
    assert.equal(await page.locator("#startButton").isEnabled(),true);
    await page.screenshot({path:path.join(output,"desktop.png"),fullPage:true});
    await page.click("#startButton");
    await page.fill("#experimentName","브라우저 검증 · 표준 코스");
    await page.click('#startForm button[type="submit"]');
    await page.waitForFunction(()=>!document.querySelector("#experimentActive").hidden);
    await page.click('[data-mark="SPIN"]');
    await page.waitForFunction(()=>document.querySelector("#eventList").textContent.includes("탈수"));
    await page.fill("#noteInput","<script>alert(1)</script>");
    await page.click('#noteForm button');
    await page.waitForFunction(()=>document.querySelector("#eventList").textContent.includes("<script>"));
    assert.equal(await page.locator("#eventList script").count(),0);
    await page.click('[data-mark="FINISH"]');
    await page.waitForFunction(()=>document.querySelector("#eventList").textContent.includes("실제 종료"));
    assert.equal(await page.locator("#experimentActive").isVisible(),true,"actual finish does not close collection");
    const downloadPromise=page.waitForEvent("download");
    await page.click("#eventsCsv");
    const download=await downloadPromise;
    const csv=fs.readFileSync(await download.path(),"utf8");
    assert.ok(csv.includes("DEMO")&&csv.includes("SPIN")&&csv.includes("<script>"));
    page.once("dialog",dialog=>dialog.accept());
    await page.click("#finishButton");
    await page.waitForFunction(()=>!document.querySelector("#experimentIdle").hidden);
    await page.reload();
    await page.click("#demoMode");
    await page.waitForFunction(()=>document.querySelector("#eventList").textContent.includes("브라우저")||document.querySelector("#eventList").textContent.includes("탈수"));
    assert.ok((await page.locator("#experimentSelect").textContent()).includes("브라우저 검증"));
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:path.join(output,"mobile.png"),fullPage:true});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,"no mobile page overflow");
    await page.setViewportSize({width:1440,height:1080});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,"no desktop page overflow");
    await page.route("**/api/**",route=>route.abort());
    await page.click("#liveMode");
    await page.waitForFunction(()=>document.querySelector("#connectionStatus").textContent==="연결 실패");
    assert.equal(await page.locator("#liveMode").getAttribute("aria-pressed"),"true","failure never silently switches to demo");
    assert.equal(await page.locator("#startButton").isEnabled(),false);
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({passed:true,checks:["waiting/stale/offline states","demo isolation","experiment markers and memo safety","separate actual finish and collection end","CSV contents","reload persistence","390px and 1440px layouts","no browser errors"],screenshots:output}));
  }finally{await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
