package com.healthbutler.service.impl;

import com.alibaba.fastjson.JSON;
import com.healthbutler.entity.*;
import com.healthbutler.exception.BusinessException;
import com.healthbutler.mapper.*;
import com.healthbutler.service.ExportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportServiceImpl implements ExportService {

    @Autowired
    private HealthReportMapper healthReportMapper;
    @Autowired
    private HealthItemMapper healthItemMapper;
    @Autowired
    private ItemDictionaryMapper itemDictionaryMapper;
    @Autowired
    private AiDiagnosisMapper aiDiagnosisMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public String generateHtmlReport(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");

        List<HealthReport> reports = healthReportMapper.selectList(
                new LambdaQueryWrapper<HealthReport>()
                        .eq(HealthReport::getUserId, userId)
                        .orderByAsc(HealthReport::getReportDate));

        List<Map<String, Object>> reportData = new ArrayList<>();
        Map<String, Map<String, Object>> indicatorMap = new LinkedHashMap<>();

        for (HealthReport report : reports) {
            List<HealthItem> items = healthItemMapper.selectList(
                    new LambdaQueryWrapper<HealthItem>()
                            .eq(HealthItem::getReportId, report.getId()));

            List<Map<String, Object>> itemList = new ArrayList<>();
            for (HealthItem item : items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("name", item.getStandardName() != null ? item.getStandardName() : item.getOriginalName());
                im.put("category", item.getCategory() != null ? item.getCategory() : "其他");
                im.put("value", item.getValue());
                im.put("unit", item.getUnit() != null ? item.getUnit() : "");
                im.put("ref", item.getReferenceRange() != null ? item.getReferenceRange() : "");
                im.put("status", item.getStatus() != null ? item.getStatus() : "normal");
                itemList.add(im);

                // 收集唯一指标（保留最新一条的基本信息）
                String key = item.getStandardName() != null ? item.getStandardName() : item.getOriginalName();
                if (key == null) continue;
                if (!indicatorMap.containsKey(key)) {
                    Map<String, Object> ind = new LinkedHashMap<>(im);
                    ind.put("_reportDate", report.getReportDate() != null ? report.getReportDate().toString() : "");
                    indicatorMap.put(key, ind);
                } else {
                    String existingDate = (String) indicatorMap.get(key).get("_reportDate");
                    String thisDate = report.getReportDate() != null ? report.getReportDate().toString() : "";
                    if (thisDate.compareTo(existingDate) > 0) {
                        Map<String, Object> ind = new LinkedHashMap<>(im);
                        ind.put("_reportDate", thisDate);
                        indicatorMap.put(key, ind);
                    }
                }
            }

            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("date", report.getReportDate() != null ? report.getReportDate().toString() : "");
            rm.put("hospital", report.getHospital() != null ? report.getHospital() : "");
            rm.put("items", itemList);
            reportData.add(rm);
        }

        List<Map<String, Object>> indicators = new ArrayList<>(indicatorMap.values());
        // 收集分类
        Set<String> catSet = new LinkedHashSet<>();
        for (Map<String, Object> ind : indicators) {
            catSet.add((String) ind.getOrDefault("category", "其他"));
        }

        return buildHtml(user, reportData, indicators, catSet);
    }

    private String buildHtml(User user, List<Map<String, Object>> reports,
                              List<Map<String, Object>> indicators, Set<String> catSet) {
        String now = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String userName = user.getUsername() != null ? user.getUsername() : "";
        int reportCount = reports.size();
        int indicatorCount = indicators.size();

        String reportsJson = JSON.toJSONString(reports);
        String indicatorsJson = JSON.toJSONString(indicators);
        String categoriesJson = JSON.toJSONString(
                catSet.stream().map(c -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("key", c); m.put("label", c); return m;
                }).collect(Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n");
        sb.append("<title>体检趋势分析 — ").append(now).append("</title>\n");
        css(sb);
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"hdr\"><div><h1>📊 体检趋势分析</h1>");
        sb.append("<div class=\"sub\">").append(esc(userName)).append(" · ");
        sb.append(indicatorCount).append(" 项指标 · ").append(reportCount).append(" 份报告</div></div>");
        sb.append("<input class=\"q\" id=\"q\" placeholder=\"搜索指标...\" oninput=\"F()\">");
        sb.append("</div>\n");
        sb.append("<div class=\"ct\" id=\"ct\"></div>\n");
        sb.append("<div class=\"st\" id=\"st\" style=\"display:none\"></div>\n");
        sb.append("<div class=\"grid\" id=\"g\"></div>\n");
        sb.append("<div id=\"ca\"><div class=\"empty\"><div class=\"ic\">📈</div>请点击指标卡片查看趋势</div></div>\n");
        sb.append("<script>\n");
        sb.append("var R=").append(reportsJson).append(";\n");
        sb.append("var I=").append(indicatorsJson).append(";\n");
        sb.append("var IC={\"血常规\":\"🩸\",\"肝功能\":\"🫀\",\"肾功能\":\"🫘\",\"血脂\":\"🫧\",\"血糖\":\"🍬\",\"血压\":\"💉\",\"甲状腺\":\"🦋\",\"电解质\":\"⚗️\",\"其他\":\"📋\"};\n");
        sb.append("var CG=[{\"key\":\"all\",\"label\":\"全部\",\"icon\":\"📋\"}].concat(").append(categoriesJson).append(".map(function(c){c.icon=IC[c.key]||'📊';return c}));\n");
        sb.append("var CC='all',S=[];\n");
        chartJs(sb);
        initJs(sb);
        sb.append("</script>\n</body>\n</html>");
        return sb.toString();
    }

    private void css(StringBuilder s) {
        s.append("<style>\n");
        s.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        s.append("body{font-family:-apple-system,BlinkMacSystemFont,'SF Pro Display',system-ui,sans-serif;background:#f5f5f7;color:#1d1d1f;padding:16px;-webkit-font-smoothing:antialiased}\n");
        s.append(".hdr{display:flex;justify-content:space-between;align-items:center;padding:16px 20px;background:rgba(255,255,255,0.72);backdrop-filter:saturate(180%) blur(20px);border-radius:16px;border:.5px solid rgba(0,0,0,.06);margin-bottom:20px}\n");
        s.append(".hdr h1{font-size:20px;font-weight:700}\n");
        s.append(".hdr .sub{font-size:12px;color:#86868b}\n");
        s.append(".q{width:200px;padding:8px 12px;border:1px solid #e5e5ea;border-radius:10px;font-size:13px;outline:none;font-family:inherit}\n");
        s.append(".q:focus{border-color:#007aff}\n");
        s.append(".ct{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}\n");
        s.append(".ct span{padding:6px 14px;border-radius:20px;font-size:13px;cursor:pointer;background:rgba(255,255,255,0.72);border:.5px solid rgba(0,0,0,.06);transition:all .2s;user-select:none}\n");
        s.append(".ct span.ac{background:#007aff;color:#fff;border-color:#007aff}\n");
        s.append(".ct span:hover:not(.ac){background:rgba(0,122,255,.08)}\n");
        s.append(".st{margin-bottom:12px;display:flex;flex-wrap:wrap;gap:6px}\n");
        s.append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px;margin-bottom:20px}\n");
        s.append(".card{background:#fff;border-radius:14px;padding:14px;cursor:pointer;border:1px solid transparent;transition:all .2s;position:relative;box-shadow:0 1px 3px rgba(0,0,0,.04)}\n");
        s.append(".card:hover{border-color:#007aff;box-shadow:0 4px 12px rgba(0,122,255,.1)}\n");
        s.append(".card.sel{background:#f0f5ff;border-color:#007aff}\n");
        s.append(".card .nm{font-size:14px;font-weight:600;margin-bottom:4px}\n");
        s.append(".card .ctg{font-size:11px;color:#86868b}\n");
        s.append(".card .vl{font-size:20px;font-weight:700;margin-top:6px}\n");
        s.append(".card .un{font-size:12px;color:#86868b}\n");
        s.append(".card .abn{position:absolute;top:8px;right:8px;width:8px;height:8px;border-radius:50%;background:#ff3b30}\n");
        s.append(".selt{padding:4px 10px;border-radius:12px;font-size:12px;cursor:pointer}\n");
        s.append(".cc{background:#fff;border-radius:16px;padding:20px;margin-bottom:16px;box-shadow:0 2px 12px rgba(0,0,0,.04)}\n");
        s.append(".cch{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}\n");
        s.append(".cct{font-size:16px;font-weight:600}\n");
        s.append(".ccm{font-size:12px;color:#86868b}\n");
        s.append(".ccb{width:100%;height:400px;position:relative;overflow:hidden}\n");
        s.append(".ccb svg{width:100%;height:100%}\n");
        s.append(".tip{position:absolute;background:rgba(255,255,255,0.96);border:1px solid #e8e8e8;border-radius:8px;padding:8px 12px;font-size:13px;pointer-events:none;display:none;z-index:10;white-space:nowrap}\n");
        s.append(".empty{text-align:center;padding:60px 0;color:#86868b;font-size:14px}\n");
        s.append(".empty .ic{font-size:48px;margin-bottom:12px}\n");
        s.append("@media(max-width:600px){body{padding:8px}.grid{grid-template-columns:repeat(auto-fill,minmax(140px,1fr))}.ccb{height:280px}.q{width:120px}}\n");
        s.append("</style>\n");
    }

    /** Pure SVG chart — no external dependencies, works offline */
    private void chartJs(StringBuilder s) {
        s.append("function F(){var q=(ge('q').value||'').toLowerCase();");
        s.append("document.querySelectorAll('.card').forEach(function(c){");
        s.append("var n=(c.getAttribute('data-n')||'').toLowerCase();");
        s.append("var t=c.getAttribute('data-c')||'';");
        s.append("c.style.display=(n.indexOf(q)>=0&&(CC==='all'||t===CC))?'':'none'});}\n");

        s.append("function SC(k){CC=k;");
        s.append("document.querySelectorAll('.ct span').forEach(function(t){t.classList.toggle('ac',t.getAttribute('data-k')===k)});F();}\n");

        s.append("function U(){var e=ge('st');if(S.length===0){e.style.display='none';e.innerHTML='';return;}");
        s.append("e.style.display='flex';var h='';S.forEach(function(n){");
        s.append("var d=I.find(function(i){return i.name===n})||{};");
        s.append("var ab=d.status&&d.status!=='normal';");
        s.append("var bg=ab?'background:#fff2f0;color:#ff4d4f;border:1px solid #ffccc7':'background:#e6f7ff;color:#1890ff;border:1px solid #91d5ff';");
        s.append("var tn=n.replace(/'/g,'&#39;').replace(/\"/g,'&quot;');");
        s.append("h+='<span class=\"selt\" style=\"'+bg+'\" title=\"'+tn+'\">'+n.replace(/</g,'&lt;')+(ab?' ⚠':'')+' <b style=\"cursor:pointer;margin-left:4px\" onclick=\"event.stopPropagation();TS(\\''+tn+'\\')\">✕</b></span>'});");
        s.append("e.innerHTML=h;}\n");

        s.append("function TS(n){var i=S.indexOf(n);if(i>=0)S.splice(i,1);else S.push(n);");
        s.append("document.querySelectorAll('.card').forEach(function(c){c.classList.toggle('sel',S.indexOf(c.getAttribute('data-n'))>=0)});");
        s.append("U();RC();}\n");

        // SVG chart rendering
        s.append("function RC(){var a=ge('ca');if(S.length===0){a.innerHTML='<div class=\"empty\"><div class=\"ic\">📈</div>请点击指标卡片查看趋势</div>';return;}");
        s.append("a.innerHTML='';var ok=false;S.forEach(function(nm){");
        s.append("var ind=I.find(function(i){return i.name===nm})||{};");
        s.append("var pts=[];R.forEach(function(r){(r.items||[]).forEach(function(it){");
        s.append("if((it.name===nm||it.name.trim()===nm.trim())&&it.value!=null)pts.push({d:r.date,v:it.value,s:it.status});});});");
        s.append("pts.sort(function(a,b){return a.d.localeCompare(b.d)});");
        s.append("if(pts.length===0){var ec=ce('div');ec.className='cc';");
        s.append("ec.innerHTML='<div class=\"cch\"><span class=\"cct\">'+nm+'</span><span class=\"ccm\">无历史数据</span></div><div style=\"padding:20px;text-align:center;color:#999;\">该指标在现有报告中未找到对应数据</div>';");
        s.append("a.appendChild(ec);return;}ok=true;");
        // Parse reference range
        s.append("var rs=ind.ref||'',mn=null,mx=null;");
        s.append("if(rs.indexOf('-')>0){var p=rs.split('-');mn=parseFloat(p[0]);mx=parseFloat(p[1]);}");
        s.append("else if(rs.charAt(0)==='<')mx=parseFloat(rs.substring(1));");
        s.append("else if(rs.charAt(0)==='>')mn=parseFloat(rs.substring(1));");
        s.append("var vs=pts.map(function(p){return p.v});");
        s.append("var y0=Math.floor((mn!=null?Math.min(mn,Math.min.apply(null,vs)):Math.min.apply(null,vs))*0.9);");
        s.append("var y1=Math.ceil((mx!=null?Math.max(mx,Math.max.apply(null,vs)):Math.max.apply(null,vs))*1.1);");
        s.append("if(y0==y1)y1=y0+1;");
        // Build chart card
        s.append("var card=ce('div');card.className='cc';");
        s.append("card.innerHTML='<div class=\"cch\"><span class=\"cct\">'+nm+'</span><span class=\"ccm\">'+(ind.category||'')+' | 参考: '+(ind.ref||'--')+' | 单位: '+(ind.unit||'--')+'</span></div><div class=\"ccb\" id=\"cb_'+nm.replace(/[^a-zA-Z0-9\\u4e00-\\u9fff]/g,'_')+'\"></div>';");
        s.append("a.appendChild(card);");
        // Draw SVG chart with tooltip
        s.append("var box=card.querySelector('.ccb');");
        s.append("var tip=ce('div');tip.className='tip';box.appendChild(tip);");
        s.append("var W=box.clientWidth,H=box.clientHeight;");
        s.append("var M={t:20,r:30,b:50,l:60};");
        s.append("var cw=W-M.l-M.r,ch=H-M.t-M.b;");
        s.append("var svg='<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"'+W+'\" height=\"'+H+'\" viewBox=\"0 0 '+W+' '+H+'\">';");
        s.append("svg+='<defs><linearGradient id=\"ag\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0%\" stop-color=\"rgba(24,144,255,0.18)\"/><stop offset=\"100%\" stop-color=\"rgba(24,144,255,0.02)\"/></linearGradient></defs>';");
        // Y-axis grid
        s.append("var ytick=5;var ystep=(y1-y0)/ytick;");
        s.append("for(var i=0;i<=ytick;i++){var yv=y0+i*ystep;var yp=M.t+(1-(yv-y0)/(y1-y0))*ch;");
        s.append("svg+='<line x1=\"'+M.l+'\" y1=\"'+yp+'\" x2=\"'+(W-M.r)+'\" y2=\"'+yp+'\" stroke=\"#f0f0f0\" stroke-width=\"1\"/>';");
        s.append("svg+='<text x=\"'+(M.l-8)+'\" y=\"'+(yp+4)+'\" text-anchor=\"end\" font-size=\"11\" fill=\"#999\">'+yv.toFixed(1)+'</text>'}");
        // Reference range band
        s.append("if(mn!=null&&mx!=null){var r0=M.t+(1-(mx-y0)/(y1-y0))*ch;var r1=M.t+(1-(mn-y0)/(y1-y0))*ch;");
        s.append("svg+='<rect x=\"'+M.l+'\" y=\"'+Math.min(r0,r1)+'\" width=\"'+cw+'\" height=\"'+Math.abs(r1-r0)+'\" fill=\"rgba(82,196,26,0.1)\"/>'}");
        // Reference lines
        s.append("if(mn!=null){var ry=M.t+(1-(mn-y0)/(y1-y0))*ch;");
        s.append("svg+='<line x1=\"'+M.l+'\" y1=\"'+ry+'\" x2=\"'+(W-M.r)+'\" y2=\"'+ry+'\" stroke=\"#faad14\" stroke-dasharray=\"5,5\" stroke-width=\"1\"/>';");
        s.append("svg+='<text x=\"'+(W-M.r+4)+'\" y=\"'+(ry+4)+'\" font-size=\"10\" fill=\"#faad14\">下限 '+mn+'</text>'}");
        s.append("if(mx!=null){var ry2=M.t+(1-(mx-y0)/(y1-y0))*ch;");
        s.append("svg+='<line x1=\"'+M.l+'\" y1=\"'+ry2+'\" x2=\"'+(W-M.r)+'\" y2=\"'+ry2+'\" stroke=\"#ff4d4f\" stroke-dasharray=\"5,5\" stroke-width=\"1\"/>';");
        s.append("svg+='<text x=\"'+(W-M.r+4)+'\" y=\"'+(ry2+4)+'\" font-size=\"10\" fill=\"#ff4d4f\">上限 '+mx+'</text>'}");
        // Build data points array
        s.append("var px=[];for(var j=0;j<pts.length;j++){");
        s.append("var xp=M.l+(j/(Math.max(pts.length-1,1)))*cw;");
        s.append("var yp=M.t+(1-(pts[j].v-y0)/(y1-y0))*ch;");
        s.append("px.push({x:xp,y:yp,d:pts[j].d,v:pts[j].v,s:pts[j].s});");
        // Date label
        s.append("var lb=pts[j].d;if(lb.length>10)lb=lb.slice(5);");
        s.append("var rx=pts.length>6?30:0;");
        s.append("svg+='<text x=\"'+xp+'\" y=\"'+(H-M.b+18)+'\" text-anchor=\"'+(rx>0?'end':'middle')+'\" font-size=\"11\" fill=\"#666\" transform=\"rotate('+rx+','+xp+','+(H-M.b+18)+')\">'+lb+'</text>';}");
        // Smooth area fill
        s.append("if(pts.length>1){");
        s.append("var ap='M'+px[0].x+','+(H-M.b)+'L'+px.map(function(p){return p.x+','+p.y}).join('L')+'L'+px[px.length-1].x+','+(H-M.b)+'Z';");
        s.append("svg+='<path d=\"'+ap+'\" fill=\"url(#ag)\"/>'}");
        s.append("else if(pts.length===1){svg+='<line x1=\"'+px[0].x+'\" y1=\"'+px[0].y+'\" x2=\"'+px[0].x+'\" y2=\"'+(H-M.b)+'\" stroke=\"rgba(24,144,255,0.08)\" stroke-width=\"2\"/>'}");
        // Smooth line
        s.append("if(pts.length>=2){");
        s.append("var lp='M'+px[0].x+','+px[0].y;");
        s.append("for(var j=1;j<px.length;j++){var cx1=px[j-1].x+(px[j].x-px[j-1].x)*0.4;var cx2=px[j].x-(px[j].x-px[j-1].x)*0.4;");
        s.append("lp+='C'+cx1+','+px[j-1].y+' '+cx2+','+px[j].y+' '+px[j].x+','+px[j].y;}");
        s.append("svg+='<path d=\"'+lp+'\" fill=\"none\" stroke=\"#1890ff\" stroke-width=\"2.5\" stroke-linejoin=\"round\"/>'}");
        // Data points with hover zones
        s.append("for(var j=0;j<px.length;j++){var r=px.length<2?7:4;");
        s.append("var isAb=px[j].s&&px[j].s!=='normal';");
        s.append("svg+='<circle cx=\"'+px[j].x+'\" cy=\"'+px[j].y+'\" r=\"'+r+'\" fill=\"'+(isAb?'#ff4d4f':'#1890ff')+'\" stroke=\"#fff\" stroke-width=\"2\" style=\"pointer-events:none\"/>';");
        // Invisible large hover target
        s.append("svg+='<circle cx=\"'+px[j].x+'\" cy=\"'+px[j].y+'\" r=\"16\" fill=\"transparent\" stroke=\"none\" class=\"hz\" data-i=\"'+j+'\" style=\"cursor:pointer\"/>'}");
        s.append("box.innerHTML=svg+'</svg>';");
        // Hover tooltip logic
        s.append("var hzs=box.querySelectorAll('.hz');");
        s.append("for(var k=0;k<hzs.length;k++){hzs[k].addEventListener('mouseenter',function(e){");
        s.append("var j=parseInt(this.getAttribute('data-i'));var p=px[j];");
        s.append("var st=p.s==='normal'?'🟢 正常':p.s==='high'?'🔴 偏高':'🟡 偏低';");
        s.append("var h='<div style=\"font-weight:600;margin-bottom:6px\">'+p.d+'</div>';");
        s.append("h+='<div style=\"margin:3px 0\">'+st+' '+nm+'：'+p.v+' '+(ind.unit||'')+'</div>';");
        s.append("if(mn!=null||mx!=null)h+='<div style=\"margin-top:6px;padding-top:6px;border-top:1px solid #f0f0f0;color:#999;font-size:11px\">参考：'+(ind.ref||'')+'</div>';");
        s.append("tip.innerHTML=h;tip.style.display='block';");
        s.append("var rx=Math.min(e.offsetX+16,W-tip.offsetWidth-8);");
        s.append("var ry=Math.max(0,e.offsetY-tip.offsetHeight-12);");
        s.append("tip.style.left=rx+'px';tip.style.top=ry+'px';});");
        s.append("hzs[k].addEventListener('mouseleave',function(){tip.style.display='none';});}");
        // End forEach
        s.append("});if(!ok){a.innerHTML='<div class=\"empty\"><div class=\"ic\">📭</div>选中的指标没有可用的历史数据</div>';}}\n");

        s.append("function ge(id){return document.getElementById(id)}\n");
        s.append("function ce(t){return document.createElement(t)}\n");
    }

    private void initJs(StringBuilder s) {
        s.append("try{");
        // Category tags
        s.append("var ct=ge('ct');CG.forEach(function(c){");
        s.append("var e=ce('span');if(c.key==='all')e.className='ac';e.setAttribute('data-k',c.key);");
        s.append("e.textContent=(c.icon||'📊')+' '+c.label;e.onclick=function(){SC(c.key)};ct.appendChild(e)});");
        // Indicator cards
        s.append("var g=ge('g');I.forEach(function(d){");
        s.append("var e=ce('div');e.className='card';");
        s.append("e.setAttribute('data-n',d.name||'');e.setAttribute('data-c',d.category||'其他');");
        s.append("e.onclick=function(){TS(d.name)};");
        s.append("var ab=d.status&&d.status!=='normal'?'<div class=\"abn\"></div>':'';");
        s.append("e.innerHTML=ab+'<div class=\"nm\">'+(d.name||'').replace(/</g,'&lt;')+'</div><div class=\"ctg\">'+(d.category||'其他')+'</div><div class=\"vl\">'+(d.value!=null?d.value:'--')+'<span class=\"un\"> '+(d.unit||'')+'</span></div>';");
        s.append("g.appendChild(e)});");
        s.append("}catch(e){ge('g').innerHTML='<div class=\"empty\"><div class=\"ic\">❌</div>'+e.message+'</div>'}\n");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
