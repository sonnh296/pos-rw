#!/usr/bin/env python3
"""Sinh file JMX mới với Thread Group SS1-SS3 + giữ P1-P3 disabled."""

import xml.etree.ElementTree as ET
from xml.dom import minidom
import os

RESULT_DIR = "/Users/mac2019/Desktop/pos/boost/jmeter/results"
INPUTS_DIR = "/Users/mac2019/Desktop/pos/boost/jmeter/inputs"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "customer-demo-thread-comparison.jmx")

# --- Helpers ---
def prop(tag, name, value):
    e = ET.SubElement(tag, "stringProp", name=name)
    e.text = str(value)
    return e

def bool_prop(tag, name, value):
    e = ET.SubElement(tag, "boolProp", name=name)
    e.text = "true" if value else "false"
    return e

def udv_arg(coll, var_name, var_value):
    ep = ET.SubElement(coll, "elementProp", name=var_name, elementType="Argument")
    prop(ep, "Argument.name", var_name)
    prop(ep, "Argument.value", str(var_value))
    prop(ep, "Argument.metadata", "=")

def add_save_config(parent):
    obj = ET.SubElement(parent, "objProp")
    ET.SubElement(obj, "name").text = "saveConfig"
    val = ET.SubElement(obj, "value")
    val.set("class", "SampleSaveConfiguration")
    for field in ["time","latency","timestamp","success","label","code","message",
                   "threadName","dataType","assertions","fieldNames","bytes",
                   "sentBytes","url","threadCounts","idleTime","connectTime"]:
        ET.SubElement(val, field).text = "true"
    for field in ["encoding","subresults","responseData","samplerData","xml",
                   "responseHeaders","requestHeaders","responseDataOnError"]:
        ET.SubElement(val, field).text = "false"
    ET.SubElement(val, "saveAssertionResultsFailureMessage").text = "true"
    ET.SubElement(val, "assertionsResultsToSave").text = "0"

def add_result_writer(hash_tree, writer_name, filename, enabled=True):
    rc = ET.SubElement(hash_tree, "ResultCollector",
                       guiclass="SimpleDataWriter", testclass="ResultCollector",
                       testname=writer_name, enabled=str(enabled).lower())
    bool_prop(rc, "ResultCollector.error_logging", False)
    add_save_config(rc)
    prop(rc, "filename", filename)
    ET.SubElement(hash_tree, "hashTree")

def add_csv(hash_tree, csv_name, csv_file, var_names="customerId"):
    csv = ET.SubElement(hash_tree, "CSVDataSet",
                        guiclass="TestBeanGUI", testclass="CSVDataSet",
                        testname=csv_name, enabled="true")
    prop(csv, "delimiter", ",")
    prop(csv, "fileEncoding", "UTF-8")
    prop(csv, "filename", csv_file)
    bool_prop(csv, "ignoreFirstLine", True)
    bool_prop(csv, "quotedData", False)
    bool_prop(csv, "recycle", True)
    prop(csv, "shareMode", "shareMode.all")
    bool_prop(csv, "stopThread", False)
    prop(csv, "variableNames", var_names)
    ET.SubElement(hash_tree, "hashTree")

def add_http_sampler(hash_tree, sampler_name, path, body_json):
    s = ET.SubElement(hash_tree, "HTTPSamplerProxy",
                      guiclass="HttpTestSampleGui", testclass="HTTPSamplerProxy",
                      testname=sampler_name, enabled="true")
    prop(s, "HTTPSampler.path", path)
    prop(s, "HTTPSampler.method", "POST")
    bool_prop(s, "HTTPSampler.postBodyRaw", True)
    args_ep = ET.SubElement(s, "elementProp", name="HTTPsampler.Arguments", elementType="Arguments")
    coll = ET.SubElement(args_ep, "collectionProp", name="Arguments.arguments")
    arg = ET.SubElement(coll, "elementProp", name="", elementType="HTTPArgument")
    bool_prop(arg, "HTTPArgument.always_encode", False)
    prop(arg, "Argument.value", body_json)
    prop(arg, "Argument.metadata", "=")
    ET.SubElement(hash_tree, "hashTree")

def add_jsr223_random_amount(hash_tree, preprocessor_name="Sinh số tiền ngẫu nhiên"):
    """Adds a JSR223 PreProcessor that generates a random amount and stores in vars."""
    pp = ET.SubElement(hash_tree, "JSR223PreProcessor",
                       guiclass="TestBeanGUI", testclass="JSR223PreProcessor",
                       testname=preprocessor_name, enabled="true")
    prop(pp, "cacheKey", "true")
    prop(pp, "filename", "")
    prop(pp, "parameters", "")
    prop(pp, "scriptLanguage", "groovy")
    script = ('import java.util.concurrent.ThreadLocalRandom\n'
              'int minAmt = Integer.parseInt(vars.get("ssMinAmount") ?: "20")\n'
              'int maxAmt = Integer.parseInt(vars.get("ssMaxAmount") ?: "200")\n'
              'int amt = ThreadLocalRandom.current().nextInt(minAmt, maxAmt + 1)\n'
              'vars.put("randomAmount", String.valueOf(amt))\n')
    prop(pp, "script", script)
    ET.SubElement(hash_tree, "hashTree")

def mk_thread_group(name, threads_var, ramp_var, loops_var, enabled=False):
    tg = ET.Element("ThreadGroup",
                     guiclass="ThreadGroupGui", testclass="ThreadGroup",
                     testname=name, enabled=str(enabled).lower())
    prop(tg, "ThreadGroup.num_threads", threads_var)
    prop(tg, "ThreadGroup.ramp_time", ramp_var)
    bool_prop(tg, "ThreadGroup.same_user_on_next_iteration", True)
    prop(tg, "ThreadGroup.on_sample_error", "continue")
    lc = ET.SubElement(tg, "elementProp",
                       name="ThreadGroup.main_controller", elementType="LoopController",
                       guiclass="LoopControlPanel", testclass="LoopController",
                       testname="Loop Controller")
    prop(lc, "LoopController.loops", loops_var)
    bool_prop(lc, "LoopController.continue_forever", False)
    return tg

def add_old_thread_group(root_ht, name, csv_name, csv_file, sampler_name, path, txn_prefix, jtl_name):
    """Thêm Thread Group cũ (P1/P2) disabled."""
    tg = mk_thread_group(name, "${threads}", "${rampUpSec}", "${loops}", enabled=False)
    root_ht.append(tg)
    ht = ET.SubElement(root_ht, "hashTree")
    add_csv(ht, csv_name, "${inputsDir}/" + csv_file)
    body = '{"customerId":"${customerId}","transactionId":"' + txn_prefix + '-${__threadNum}-${__counter(TRUE,)}","amount":${amount}}'
    add_http_sampler(ht, sampler_name, path, body)
    add_result_writer(ht, f"Ghi kết quả - {jtl_name}", "${resultDir}/" + jtl_name + ".jtl")

def add_old_prod_group(root_ht, name, sampler_name, path, txn_prefix, jtl_name):
    """Thêm Thread Group cũ P3 disabled."""
    tg = mk_thread_group(name, "${prodThreads}", "${prodRampUpSec}", "${prodLoops}", enabled=False)
    root_ht.append(tg)
    ht = ET.SubElement(root_ht, "hashTree")
    body = ('{"customerId":"prod-user-${__UUID()}","transactionId":"'
            + txn_prefix + '-${__threadNum}-${__counter(TRUE,)}",'
            '"amount":${__Random(${prodMinAmount},${prodMaxAmount},)}}')
    add_http_sampler(ht, sampler_name, path, body)
    add_result_writer(ht, f"Ghi kết quả - {jtl_name}", "${resultDir}/" + jtl_name + ".jtl")

def add_ss_group(root_ht, group_name, enabled,
                 csv1_file, csv1_var, sampler1_name, path1, txn1_prefix, jtl1,
                 csv2_file, csv2_var, sampler2_name, path2, txn2_prefix, jtl2):
    """Thêm Thread Group so sánh (SS) với 2 sampler trong mỗi iteration."""
    tg = mk_thread_group(group_name, "${ssThreads}", "${ssRampUpSec}", "${ssLoops}", enabled=enabled)
    root_ht.append(tg)
    ht = ET.SubElement(root_ht, "hashTree")

    # CSV cho sampler 1
    add_csv(ht, f"CSV - {csv1_var}", "${inputsDir}/" + csv1_file, csv1_var)
    # CSV cho sampler 2
    add_csv(ht, f"CSV - {csv2_var}", "${inputsDir}/" + csv2_file, csv2_var)
    # Random amount preprocessor
    add_jsr223_random_amount(ht)

    # Sampler 1
    body1 = ('{"customerId":"${' + csv1_var + '}","transactionId":"'
             + txn1_prefix + '-${__threadNum}-${__counter(TRUE,)}-${__UUID()}",'
             '"amount":${randomAmount}}')
    add_http_sampler(ht, sampler1_name, path1, body1)
    add_result_writer(ht, f"Ghi kết quả - {jtl1}", "${resultDir}/" + jtl1 + ".jtl")

    # Sampler 2
    body2 = ('{"customerId":"${' + csv2_var + '}","transactionId":"'
             + txn2_prefix + '-${__threadNum}-${__counter(TRUE,)}-${__UUID()}",'
             '"amount":${randomAmount}}')
    add_http_sampler(ht, sampler2_name, path2, body2)
    add_result_writer(ht, f"Ghi kết quả - {jtl2}", "${resultDir}/" + jtl2 + ".jtl")


def add_listener(root_ht, gui, testname, enabled=True):
    rc = ET.SubElement(root_ht, "ResultCollector",
                       guiclass=gui, testclass="ResultCollector",
                       testname=testname, enabled=str(enabled).lower())
    bool_prop(rc, "ResultCollector.error_logging", False)
    add_save_config(rc)
    prop(rc, "filename", "")
    ET.SubElement(root_ht, "hashTree")


def build():
    root = ET.Element("jmeterTestPlan", version="1.2", properties="5.0", jmeter="5.6.3")
    root_ht_outer = ET.SubElement(root, "hashTree")

    # --- Test Plan ---
    tp = ET.SubElement(root_ht_outer, "TestPlan",
                       guiclass="TestPlanGui", testclass="TestPlan",
                       testname="Demo So Sánh Thread & Lock - Hệ Thống POS")
    prop(tp, "TestPlan.comments",
         "SS1: So sánh Lock vs Không-Lock (Platform Thread) - 1000 lần, amount ngẫu nhiên\n"
         "SS2: So sánh Lock vs Không-Lock (Virtual Thread) - 1000 lần\n"
         "SS3: So sánh Platform vs Virtual (có Lock) - 1000 lần\n"
         "P1-P3: Kịch bản cũ (disabled) - giữ để tương thích ngược\n"
         "QUAN TRỌNG: Chỉ bật 1 Thread Group mỗi lần chạy.")
    args_ep = ET.SubElement(tp, "elementProp",
                            name="TestPlan.user_defined_variables",
                            elementType="Arguments",
                            guiclass="ArgumentsPanel", testclass="Arguments",
                            testname="Biến cấu hình chung")
    coll = ET.SubElement(args_ep, "collectionProp", name="Arguments.arguments")

    variables = [
        ("host", "localhost"),
        ("port", "8080"),
        ("protocol", "http"),
        ("connectTimeoutMs", "5000"),
        ("responseTimeoutMs", "15000"),
        ("resultDir", RESULT_DIR),
        ("inputsDir", INPUTS_DIR),
        # Biến cho SS (So Sánh)
        ("ssThreads", "50"),
        ("ssRampUpSec", "5"),
        ("ssLoops", "20"),
        ("ssMinAmount", "20"),
        ("ssMaxAmount", "200"),
        # Biến cũ (P1/P2)
        ("threads", "40"),
        ("rampUpSec", "5"),
        ("loops", "5"),
        ("amount", "50"),
        ("hotspotCustomerId", "customer-race-022"),
        # Biến cũ (P3)
        ("prodThreads", "5000"),
        ("prodRampUpSec", "4"),
        ("prodLoops", "1"),
        ("prodMinAmount", "20"),
        ("prodMaxAmount", "200"),
    ]
    for vname, vval in variables:
        udv_arg(coll, vname, vval)

    root_ht = ET.SubElement(root_ht_outer, "hashTree")

    # --- HTTP Header Manager ---
    hm = ET.SubElement(root_ht, "HeaderManager",
                       guiclass="HeaderPanel", testclass="HeaderManager",
                       testname="Tiêu đề HTTP chung")
    hm_coll = ET.SubElement(hm, "collectionProp", name="HeaderManager.headers")
    for hdr_name, hdr_val in [("Content-Type", "application/json"), ("Accept", "application/json")]:
        hdr = ET.SubElement(hm_coll, "elementProp", name="", elementType="Header")
        prop(hdr, "Header.name", hdr_name)
        prop(hdr, "Header.value", hdr_val)
    ET.SubElement(root_ht, "hashTree")

    # --- HTTP Request Defaults ---
    hrd = ET.SubElement(root_ht, "ConfigTestElement",
                        guiclass="HttpDefaultsGui", testclass="ConfigTestElement",
                        testname="Cấu hình HTTP mặc định", enabled="true")
    prop(hrd, "HTTPSampler.connect_timeout", "${connectTimeoutMs}")
    prop(hrd, "HTTPSampler.response_timeout", "${responseTimeoutMs}")
    prop(hrd, "HTTPSampler.domain", "${host}")
    prop(hrd, "HTTPSampler.port", "${port}")
    prop(hrd, "HTTPSampler.protocol", "${protocol}")
    prop(hrd, "HTTPSampler.contentEncoding", "UTF-8")
    hrd_args = ET.SubElement(hrd, "elementProp", name="HTTPsampler.Arguments",
                             elementType="Arguments", guiclass="HTTPArgumentsPanel",
                             testclass="Arguments", testname="Biến HTTP")
    ET.SubElement(hrd_args, "collectionProp", name="Arguments.arguments")
    prop(hrd, "HTTPSampler.implementation", "")
    ET.SubElement(root_ht, "hashTree")

    # ============================================================
    # SS1 - So sánh Lock vs Không-Lock (Platform Thread)
    # ============================================================
    add_ss_group(root_ht,
        group_name="SS1 - So sánh Có-Khóa vs Không-Khóa (Platform Thread, 1000 lần)",
        enabled=True,
        csv1_file="ss1-nolock-hotspot.csv", csv1_var="customerIdNoLock",
        sampler1_name="POST Không-Khóa /api/rewards/platform/no-lock",
        path1="/api/rewards/platform/no-lock",
        txn1_prefix="txn-ss1-nl", jtl1="ss1-platform-nolock",
        csv2_file="ss1-lock-hotspot.csv", csv2_var="customerIdLock",
        sampler2_name="POST Có-Khóa /api/rewards/platform/lock",
        path2="/api/rewards/platform/lock",
        txn2_prefix="txn-ss1-lk", jtl2="ss1-platform-lock")

    # ============================================================
    # SS2 - So sánh Lock vs Không-Lock (Virtual Thread)
    # ============================================================
    add_ss_group(root_ht,
        group_name="SS2 - So sánh Có-Khóa vs Không-Khóa (Virtual Thread, 1000 lần)",
        enabled=False,
        csv1_file="ss2-nolock-hotspot.csv", csv1_var="customerIdNoLock",
        sampler1_name="POST Không-Khóa /api/rewards/virtual/no-lock",
        path1="/api/rewards/virtual/no-lock",
        txn1_prefix="txn-ss2-nl", jtl1="ss2-virtual-nolock",
        csv2_file="ss2-lock-hotspot.csv", csv2_var="customerIdLock",
        sampler2_name="POST Có-Khóa /api/rewards/virtual/lock",
        path2="/api/rewards/virtual/lock",
        txn2_prefix="txn-ss2-lk", jtl2="ss2-virtual-lock")

    # ============================================================
    # SS3 - So sánh Platform vs Virtual (có Lock, 1000 lần)
    # ============================================================
    add_ss_group(root_ht,
        group_name="SS3 - So sánh Platform vs Virtual (có Khóa, 1000 lần)",
        enabled=False,
        csv1_file="ss3-platform-hotspot.csv", csv1_var="customerIdPlatform",
        sampler1_name="POST Platform /api/rewards/platform/lock",
        path1="/api/rewards/platform/lock",
        txn1_prefix="txn-ss3-pl", jtl1="ss3-platform-lock",
        csv2_file="ss3-virtual-hotspot.csv", csv2_var="customerIdVirtual",
        sampler2_name="POST Virtual /api/rewards/virtual/lock",
        path2="/api/rewards/virtual/lock",
        txn2_prefix="txn-ss3-vl", jtl2="ss3-virtual-lock")

    # ============================================================
    # OLD Thread Groups (disabled) - P1
    # ============================================================
    old_p1 = [
        ("P1-A Tuần tự không khóa (baseline)", "CSV - p1-a", "p1-a-hotspot.csv",
         "POST /api/rewards/single/no-lock", "/api/rewards/single/no-lock", "txn-snl", "p1-a-single-no-lock"),
        ("P1-B Platform không khóa (kỳ vọng race)", "CSV - p1-b", "p1-b-hotspot.csv",
         "POST /api/rewards/platform/no-lock", "/api/rewards/platform/no-lock", "txn-pnl", "p1-b-platform-no-lock"),
        ("P1-C Virtual không khóa (kỳ vọng race)", "CSV - p1-c", "p1-c-hotspot.csv",
         "POST /api/rewards/virtual/no-lock", "/api/rewards/virtual/no-lock", "txn-vnl", "p1-c-virtual-no-lock"),
    ]
    for name, csv_n, csv_f, sname, path, txn, jtl in old_p1:
        add_old_thread_group(root_ht, name, csv_n, csv_f, sname, path, txn, jtl)

    # OLD Thread Groups (disabled) - P2
    old_p2 = [
        ("P2-A Tuần tự có khóa", "CSV - p2-a", "p2-a-hotspot.csv",
         "POST /api/rewards/single/lock", "/api/rewards/single/lock", "txn-sl", "p2-a-single-lock"),
        ("P2-B Platform có khóa", "CSV - p2-b", "p2-b-hotspot.csv",
         "POST /api/rewards/platform/lock", "/api/rewards/platform/lock", "txn-pl", "p2-b-platform-lock"),
        ("P2-C Virtual có khóa", "CSV - p2-c", "p2-c-hotspot.csv",
         "POST /api/rewards/virtual/lock", "/api/rewards/virtual/lock", "txn-vl", "p2-c-virtual-lock"),
    ]
    for name, csv_n, csv_f, sname, path, txn, jtl in old_p2:
        add_old_thread_group(root_ht, name, csv_n, csv_f, sname, path, txn, jtl)

    # OLD Thread Groups (disabled) - P3
    add_old_prod_group(root_ht,
        "P3-A Platform có khóa (mô phỏng sản xuất)", "POST /api/rewards/platform/lock (sản xuất)",
        "/api/rewards/platform/lock", "txn-prod-pl", "p3-a-platform-lock-prod")
    add_old_prod_group(root_ht,
        "P3-B Virtual có khóa (mô phỏng sản xuất)", "POST /api/rewards/virtual/lock (sản xuất)",
        "/api/rewards/virtual/lock", "txn-prod-vl", "p3-b-virtual-lock-prod")

    # ============================================================
    # Listeners
    # ============================================================
    add_listener(root_ht, "SummaryReport", "Báo cáo tổng hợp (Kết quả chính)")
    add_listener(root_ht, "StatVisualizer", "Báo cáo chi tiết (P95/P99)")
    add_listener(root_ht, "ViewResultsFullVisualizer", "Xem chi tiết kết quả (chỉ dùng khi gỡ lỗi)", enabled=False)

    return root


def prettify(root):
    rough = ET.tostring(root, encoding="unicode", xml_declaration=False)
    rough = '<?xml version="1.0" encoding="UTF-8"?>\n' + rough
    dom = minidom.parseString(rough)
    lines = dom.toprettyxml(indent="  ", encoding=None).split("\n")
    # Remove extra xml declaration from minidom
    if lines and lines[0].startswith("<?xml"):
        lines = lines[1:]
    result = '<?xml version="1.0" encoding="UTF-8"?>\n' + "\n".join(l for l in lines if l.strip())
    return result


if __name__ == "__main__":
    root = build()
    xml_str = prettify(root)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(xml_str)
        f.write("\n")
    print(f"✅ Đã sinh file: {OUT}")
    print(f"   Kích thước: {len(xml_str):,} bytes")
