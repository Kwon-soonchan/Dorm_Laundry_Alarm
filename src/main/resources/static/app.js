"use strict";
const $ = id => document.getElementById(id);
const hosted = location.hostname.endsWith(".chatgpt.site") || location.protocol === "file:";
const state = { mode: hosted ? "demo" : "live", machineId: 1, minutes: 15, snapshot: null,
  experiments: [], selectedId: null, events: [], busy: false, loading: false, epoch: 0, error: null };
const labels = { START: "실험 시작", WASH: "본세탁", RINSE: "헹굼", SPIN: "탈수", FINISH: "실제 종료", NOTE: "관찰 메모" };
let demoRows = [], demoLast = 0, demoBase = Date.now() - 15 * 60000, demoExperiments = [], demoEvents = [], toastTimer;
const demoKey = "laundry-lab-demo-v1";
try {
  const saved = JSON.parse(localStorage.getItem(demoKey) || "{}");
  if (Array.isArray(saved.experiments) && Array.isArray(saved.events)) {
    demoExperiments = saved.experiments; demoEvents = saved.events;
  }
} catch { /* A blocked or corrupt browser store must not stop the dashboard. */ }
function toast(message) { $("toast").textContent = message; $("toast").hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $("toast").hidden = true, 5000); }
function saveDemo() {
  try { localStorage.setItem(demoKey, JSON.stringify({experiments: demoExperiments, events: demoEvents})); }
  catch { toast("브라우저 저장이 제한되어 있습니다. 데모 기록은 이 화면을 닫으면 사라질 수 있습니다."); }
}
function time(value, date = false) {
  if (!value) return "—";
  const d = new Date(value);
  return d.toLocaleString("ko-KR", date
    ? { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }
    : { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
}
function activeExperiment() { return state.experiments.find(e => !e.endedAt); }
function selectedExperiment() { return state.experiments.find(e => String(e.id) === String(state.selectedId)); }
async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: {"Content-Type":"application/json", ...options.headers}, signal: AbortSignal.timeout(10000) });
  if (!response.ok) {
    const messages = {400:"입력한 값을 확인해 주세요.",404:"기록을 찾을 수 없습니다.",409:"실험 상태가 변경되었습니다. 새로고침 후 다시 시도하세요."};
    throw new Error(messages[response.status] || "서버 요청에 실패했습니다. 잠시 후 다시 시도하세요.");
  }
  return response.json();
}
function updateDemo() {
  const now = Date.now(), start = demoLast || demoBase;
  for (let t = start + 1000; t <= now; t += 1000) {
    const sec = (t - demoBase) / 1000, phase = sec % 900;
    const ratio = phase < 70 || phase > 810 ? 0 :
      phase > 360 && phase < 445 ? 0.01 :
      phase > 650 ? 0.72 + 0.16 * Math.sin(sec / 5) :
      0.23 + 0.17 * Math.sin(sec / 11) + 0.07 * Math.cos(sec / 3);
    const hits = Math.round(Math.max(0, Math.min(1, ratio)) * 100);
    demoRows.push({ id:t, recordedAt:new Date(t).toISOString(), sampleCount:100, vibrationCount:hits, windowMs:1000, vibrationRatio:hits/100 });
    demoLast = t;
  }
  // Keep an hour of illustrative data. Demo exports never contain sensor measurements.
  demoRows = demoRows.filter(r => Date.parse(r.recordedAt) >= now - 3600000);
  const points = demoRows.filter(r => Date.parse(r.recordedAt) >= now - state.minutes * 60000);
  return {serverTime:new Date(now).toISOString(), connection:"LIVE", machineStatus:points.at(-1)?.vibrationRatio > .05 ? "RUNNING" : "AVAILABLE",
    lastReceivedAt:demoRows.at(-1)?.recordedAt, totalSamples:demoRows.length, points};
}
async function refresh() {
  if (state.loading || state.busy) return;
  state.loading = true;
  const epoch = state.epoch;
  try {
    let snapshot, experiments, events;
    if (state.mode === "demo") {
      snapshot = updateDemo();
      experiments = demoExperiments.filter(e => e.machineId === state.machineId).slice().reverse();
    } else {
      [snapshot, experiments] = await Promise.all([
        api("/api/dashboard?machineId=" + state.machineId + "&minutes=" + state.minutes),
        api("/api/experiments?machineId=" + state.machineId)
      ]);
    }
    if (epoch !== state.epoch) return;
    let id = state.selectedId;
    if (!experiments.some(e => String(e.id) === String(id))) id = (experiments.find(e => !e.endedAt) || experiments[0])?.id ?? null;
    events = id == null ? [] : state.mode === "demo"
      ? demoEvents.filter(e => String(e.experimentId) === String(id))
      : await api("/api/experiments/" + id + "/events");
    if (epoch !== state.epoch) return;
    Object.assign(state, { snapshot, experiments, selectedId:id, events, error:null });
    render();
  } catch (error) {
    if (epoch === state.epoch) { state.error = error.message; render(); }
  } finally { state.loading = false; }
}
function render() {
  const demo = state.mode === "demo", s = state.snapshot;
  $("demoMode").classList.toggle("selected", demo); $("liveMode").classList.toggle("selected", !demo);
  $("demoMode").setAttribute("aria-pressed", demo); $("liveMode").setAttribute("aria-pressed", !demo);
  $("modeNotice").classList.toggle("error", !!state.error);
  $("modeNotice").textContent = state.error
    ? "서버 연결을 확인해 주세요. 마지막 조회값은 최신 상태가 아닐 수 있습니다. " + state.error
    : demo ? "데모 모드 · 예시 진동 데이터를 보여줍니다. 실험 기록은 이 브라우저에만 저장되며 실제 센서·DB에는 반영되지 않습니다."
    : "실시간 모드 · PC 서버의 실제 수집 데이터입니다. 5초마다 갱신하며 실험 기록은 MySQL에 저장됩니다.";
  $("refreshStatus").textContent = state.error ? "갱신 실패" : demo ? "예시 데이터 · 5초마다 갱신" : "5초마다 새로고침";
  const connected = !state.error && s?.connection === "LIVE";
  $("connectionStatus").textContent = state.error ? "연결 실패" : !s || s.connection === "WAITING" ? "수신 대기" : connected ? demo ? "데모 수신 중" : "수신 중" : "연결 확인 필요";
  $("connectionStatus").style.color = connected ? "#537c3c" : "#9b8550";
  $("lastReceived").textContent = s?.lastReceivedAt ? "마지막 수신 " + time(s.lastReceivedAt) : "아직 수신된 데이터가 없습니다.";
  $("machineStatus").textContent = !connected ? "확인 필요" : s.machineStatus === "RUNNING" ? "세탁 중" : s.machineStatus === "AVAILABLE" ? "정지 · 대기" : "상태 미확인";
  $("machineHint").textContent = connected ? "무진동 기반 임시 판정 · 완료 보장 아님" : "통신 중단을 세탁 종료로 판단하지 않습니다.";
  const last = s?.points.at(-1);
  $("ratio").textContent = last ? (last.vibrationRatio * 100).toFixed(1) : "—";
  $("totalSamples").textContent = (s?.totalSamples || 0).toLocaleString("ko-KR");
  $("sampleHint").textContent = demo ? "데모 데이터 · 최근 1시간 이내" : "기기별 텔레메트리 저장 건수";
  $("chartEmpty").hidden = !!s?.points.length;
  $("dataEmpty").hidden = !!s?.points.length;
  $("dataRows").replaceChildren();
  for (const point of (s?.points || []).slice(-10).reverse()) {
    const tr = document.createElement("tr");
    const ratio = (point.vibrationRatio * 100).toFixed(1);
    [time(point.recordedAt), point.windowMs + " ms", point.sampleCount, point.vibrationCount].forEach(value => {
      const td = document.createElement("td"); td.textContent = value; tr.append(td);
    });
    const td = document.createElement("td"), bar = document.createElement("span"), fill = document.createElement("i");
    bar.className = "ratio-bar"; fill.style.width = ratio + "%"; bar.append(fill); td.append(bar, ratio + " %"); tr.append(td);
    $("dataRows").append(tr);
  }
  const active = activeExperiment();
  $("experimentIdle").hidden = !!active; $("experimentActive").hidden = !active;
  $("experimentBadge").textContent = active ? demo ? "데모 기록 중" : "기록 중" : "대기";
  if (active) { $("activeTitle").textContent = active.title; $("experimentStarted").textContent = time(active.startedAt, true) + " 시작"; }
  updateElapsed();
  const select = $("experimentSelect");
  select.replaceChildren();
  if (!state.experiments.length) select.add(new Option("실험을 선택하세요", ""));
  state.experiments.forEach(e => select.add(new Option((e.endedAt ? "종료 · " : "기록 중 · ") + e.title, e.id)));
  if (state.selectedId != null) select.value = String(state.selectedId);
  const selected = selectedExperiment();
  $("experimentDetails").textContent = selected ? time(selected.startedAt, true) + " 시작" + (selected.endedAt ? " → " + time(selected.endedAt, true) + " 수집 종료" : " · 수집 중") : "";
  $("eventList").replaceChildren();
  $("eventsEmpty").hidden = state.events.length > 0;
  for (const event of state.events) {
    const li = document.createElement("li"), stamp = document.createElement("time"), label = document.createElement("span"), note = document.createElement("span");
    stamp.dateTime = event.recordedAt; stamp.textContent = time(event.recordedAt);
    label.className = "event-kind"; label.textContent = labels[event.kind] || event.kind;
    note.className = "event-note"; note.textContent = event.note || "";
    li.append(stamp, label, note); $("eventList").append(li);
  }
  $("eventsCsv").disabled = !selected; $("experimentCsv").disabled = !selected;
  $("startButton").disabled = state.busy || !!state.error;
  drawChart();
}
function updateElapsed() {
  const active = activeExperiment(); if (!active) return;
  const seconds = Math.max(0, Math.floor((Date.now() - Date.parse(active.startedAt))/1000));
  $("elapsed").textContent = [Math.floor(seconds/3600), Math.floor(seconds/60)%60, seconds%60].map(n => String(n).padStart(2,"0")).join(":");
}
function drawChart(hoverX) {
  const canvas = $("chart"), rect = canvas.getBoundingClientRect(), scale = Math.min(devicePixelRatio || 1, 2);
  if (!rect.width) return;
  canvas.width = rect.width * scale; canvas.height = rect.height * scale;
  const ctx = canvas.getContext("2d"); ctx.scale(scale,scale);
  const w=rect.width,h=rect.height,left=34,right=w-12,top=15,bottom=h-30;
  const points=state.snapshot?.points || [], serverTime=Date.parse(state.snapshot?.serverTime || new Date().toISOString()), from=serverTime-state.minutes*60000;
  const x = t => left + (Date.parse(t)-from)/(state.minutes*60000)*(right-left);
  const y = r => bottom - r*(bottom-top);
  ctx.font='9px "Segoe UI",sans-serif'; ctx.lineWidth=1;
  [0,.25,.5,.75,1].forEach(r=>{
    ctx.strokeStyle="#365448";ctx.beginPath();ctx.moveTo(left,y(r));ctx.lineTo(right,y(r));ctx.stroke();
    ctx.fillStyle="#88a48f";ctx.fillText(String(r*100),3,y(r)+3);
  });
  for(let i=0;i<4;i++){
    const t=from+i/3*(serverTime-from),px=left+i/3*(right-left);
    ctx.fillStyle="#88a48f";ctx.textAlign=i===0?"left":i===3?"right":"center";
    ctx.fillText(new Date(t).toLocaleTimeString("ko-KR",{hour:"2-digit",minute:"2-digit",hour12:false}),px,h-8);
  }
  ctx.textAlign="left";
  if (!points.length) return;
  // Break the line across missing telemetry rather than drawing through a disconnect.
  ctx.strokeStyle="#bddb9f";ctx.lineWidth=1.6;ctx.beginPath();
  let previous=null;
  for(const point of points){
    const px=x(point.recordedAt),py=y(point.vibrationRatio);
    if(!previous || Date.parse(point.recordedAt)-Date.parse(previous.recordedAt)>5000) ctx.moveTo(px,py);
    else ctx.lineTo(px,py);
    previous=point;
  }
  ctx.stroke();
  for(const e of state.events){
    const px=x(e.recordedAt);if(px<left||px>right)continue;
    ctx.strokeStyle="#b8cf8d55";ctx.setLineDash([3,4]);ctx.beginPath();ctx.moveTo(px,top);ctx.lineTo(px,bottom);ctx.stroke();ctx.setLineDash([]);
  }
  if(hoverX!==undefined){
    const closest=points.reduce((best,p)=>Math.abs(x(p.recordedAt)-hoverX)<Math.abs(x(best.recordedAt)-hoverX)?p:best,points[0]);
    const px=x(closest.recordedAt),py=y(closest.vibrationRatio);
    ctx.strokeStyle="#94ac86";ctx.beginPath();ctx.moveTo(px,top);ctx.lineTo(px,bottom);ctx.stroke();
    ctx.fillStyle="#def3c1";ctx.beginPath();ctx.arc(px,py,4,0,Math.PI*2);ctx.fill();
    $("chartReadout").textContent=time(closest.recordedAt)+" · "+(closest.vibrationRatio*100).toFixed(1)+"% · "+closest.vibrationCount+"/"+closest.sampleCount+" 샘플";
  }
}
async function mutate(work, success) {
  if(state.busy) return;
  if(state.loading) { toast("데이터를 갱신 중입니다. 잠시 후 다시 눌러주세요."); return; }
  state.busy=true;
  document.querySelectorAll("#experiment button, #startDialog button, .mode-switch button, #machineId").forEach(b=>b.disabled=true);
  try { await work(); if(success)toast(success); }
  catch(error){toast(error.message);}
  finally {state.busy=false;document.querySelectorAll("#experiment button, #startDialog button, .mode-switch button, #machineId").forEach(b=>b.disabled=false);await refresh();}
}
function changeMode(mode){
  if(state.busy || state.mode===mode)return;
  if(mode==="live" && hosted){$("helpDialog").showModal();return;}
  state.epoch++;Object.assign(state,{mode,snapshot:null,experiments:[],events:[],selectedId:null,error:null});
  render(); refresh();
}
$("demoMode").onclick=()=>changeMode("demo");$("liveMode").onclick=()=>changeMode("live");
$("machineId").onchange=()=>{
  const value=Number($("machineId").value);
  if(!Number.isInteger(value)||value<1||value>9999){$("machineId").value=state.machineId;toast("기기 번호는 1~9999 사이로 입력하세요.");return;}
  state.epoch++;Object.assign(state,{machineId:value,snapshot:null,experiments:[],selectedId:null,events:[],error:null});render();refresh();
};
$("minutes").onchange=()=>{state.minutes=Number($("minutes").value);state.epoch++;refresh();};
$("refreshButton").onclick=refresh;
$("startButton").onclick=()=>{
  $("experimentName").value="";$("startModeHint").textContent=state.mode==="demo"?"데모 기록은 이 브라우저에만 저장됩니다.":"선택한 기기의 실험 기록을 서버에 저장합니다.";
  $("startDialog").showModal();$("experimentName").focus();
};
$("cancelStart").onclick=()=>$("startDialog").close();
$("startForm").onsubmit=event=>{
  event.preventDefault();const title=$("experimentName").value.trim();if(!title){toast("실험 이름을 입력하세요.");return;}
  mutate(async()=>{
    let e;
    if(state.mode==="demo"){
      e={id:crypto.randomUUID(),title,machineId:state.machineId,startedAt:new Date().toISOString(),endedAt:null};
      demoExperiments.push(e);demoEvents.push({id:crypto.randomUUID(),experimentId:e.id,kind:"START",note:"",recordedAt:e.startedAt});saveDemo();
    }else e=await api("/api/experiments",{method:"POST",body:JSON.stringify({title,machineId:state.machineId})});
    state.selectedId=e.id;$("startDialog").close();
  },"실험을 시작했습니다.");
};
async function mark(kind,note=""){
  const active=activeExperiment();if(!active)return;
  await mutate(async()=>{
    if(state.mode==="demo"){demoEvents.push({id:crypto.randomUUID(),experimentId:active.id,kind,note,recordedAt:new Date().toISOString()});saveDemo();}
    else await api("/api/experiments/"+active.id+"/events",{method:"POST",body:JSON.stringify({kind,note})});
    state.selectedId=active.id;if(kind==="NOTE")$("noteInput").value="";
  },kind==="FINISH"?"실제 종료를 기록했습니다. 종료 후 5분도 더 수집해 보세요.":labels[kind]+" 시각을 기록했습니다.");
}
document.querySelectorAll("[data-mark]").forEach(b=>b.onclick=()=>mark(b.dataset.mark));
$("noteForm").onsubmit=e=>{e.preventDefault();const note=$("noteInput").value.trim();if(note)mark("NOTE",note);};
$("finishButton").onclick=()=>{
  const active=activeExperiment();if(!active)return;
  if(!confirm("실험 수집 범위를 지금 시각까지로 종료할까요? 센서 데이터 수집은 계속됩니다."))return;
  mutate(async()=>{
    if(state.mode==="demo"){active.endedAt=new Date().toISOString();saveDemo();}
    else await api("/api/experiments/"+active.id+"/finish",{method:"POST",body:"{}"});
    state.selectedId=active.id;
  },"실험 수집을 종료했습니다. CSV로 결과를 내려받을 수 있습니다.");
};
$("experimentSelect").onchange=async()=>{
  if(state.loading||state.busy)return;
  state.selectedId=$("experimentSelect").value||null;await refresh();
};
function csvCell(value){let text=String(value??"");if(/^[\s]*[=+@-]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"';}
function download(content,name){
  const url=URL.createObjectURL(new Blob(["\uFEFF"+content.replace(/^\uFEFF/,"")],{type:"text/csv;charset=utf-8"}));
  const a=document.createElement("a");a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
async function downloadServer(path,name){
  try{const response=await fetch(path,{signal:AbortSignal.timeout(30000)});if(!response.ok)throw new Error("CSV 다운로드에 실패했습니다.");download(await response.text(),name);}
  catch(error){toast(error.message);}
}
function telemetryCsv(rows){
  return "data_source,id,machine_id,recorded_at,window_ms,sample_count,vibration_count,vibration_ratio\n"+
    rows.map(r=>["DEMO",r.id,state.machineId,r.recordedAt,r.windowMs,r.sampleCount,r.vibrationCount,r.vibrationRatio].map(csvCell).join(",")).join("\n");
}
$("downloadCsv").onclick=()=>state.mode==="demo"?download(telemetryCsv(demoRows),"DEMO-vibration-telemetry.csv"):downloadServer("/api/telemetry/export.csv?machineId="+state.machineId,"vibration-telemetry.csv");
$("eventsCsv").onclick=()=>{
  const e=selectedExperiment();if(!e)return;
  if(state.mode==="live")return downloadServer("/api/experiments/"+e.id+"/events.csv","experiment-"+e.id+"-events.csv");
  const rows=demoEvents.filter(r=>r.experimentId===e.id);
  download("data_source,experiment_id,recorded_at,kind,note\n"+rows.map(r=>["DEMO",e.id,r.recordedAt,r.kind,r.note].map(csvCell).join(",")).join("\n"),"DEMO-experiment-events.csv");
};
$("experimentCsv").onclick=()=>{
  const e=selectedExperiment();if(!e)return;
  if(state.mode==="live")return downloadServer("/api/experiments/"+e.id+"/telemetry.csv","experiment-"+e.id+"-telemetry.csv");
  const rows=demoRows.filter(r=>Date.parse(r.recordedAt)>=Date.parse(e.startedAt)&&(!e.endedAt||Date.parse(r.recordedAt)<=Date.parse(e.endedAt)));
  if(!rows.length)toast("이 화면에서 생성된 최근 1시간 데모 데이터 중 해당 실험 구간이 없습니다.");
  download(telemetryCsv(rows),"DEMO-experiment-telemetry.csv");
};
$("helpButton").onclick=()=>$("helpDialog").showModal();$("closeHelp").onclick=()=>$("helpDialog").close();
$("chart").addEventListener("pointermove",e=>drawChart(e.clientX-$("chart").getBoundingClientRect().left));
$("chart").addEventListener("pointerleave",()=>{drawChart();$("chartReadout").textContent="그래프 위에 올려 구간별 값을 확인하세요.";});
new ResizeObserver(()=>drawChart()).observe($("chart"));
setInterval(()=>{if(!document.hidden)refresh();},5000);setInterval(updateElapsed,1000);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)refresh();});
render();refresh();
