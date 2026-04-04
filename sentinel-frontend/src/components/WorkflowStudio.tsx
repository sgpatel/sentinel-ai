import { useState, useEffect, useCallback } from "react";
import type { Mention } from "../types";
import type { ToastMsg } from "./Toast";
import {
  listWorkflowRules,
  createWorkflowRule,
  setWorkflowRuleEnabled,
  deleteWorkflowRule,
  dryRunWorkflow,
  executeWorkflow,
  listWorkflowExecutions,
  listWorkflowExecutionSteps,
  listKbArticles,
} from "../hooks/useSentinel";

const C = {
  green: "#22c55e",
  blue: "#3b82f6",
  orange: "#f97316",
};

type WorkflowRule = {
  id: string;
  name: string;
  enabled: boolean;
  priority: number;
  conditions?: unknown[];
  actions?: unknown[];
};

type WorkflowExecution = {
  id: string;
  status: string;
  mentionId: string;
};

type WorkflowStep = {
  id: string;
  stepType: string;
  stepName: string;
  success: boolean;
  details: unknown;
};

type KbArticle = {
  id: string;
  title: string;
};

type ActionType = "escalate" | "assign" | "notify_webhook" | "attach_kb_article";

export function WorkflowStudio({
  mentions,
  canAnalyze,
  isAdmin,
  addToast,
}: {
  mentions: Mention[];
  canAnalyze: boolean;
  isAdmin: boolean;
  addToast: (t: Omit<ToastMsg, "id">) => void;
}) {
  const [rules, setRules] = useState<WorkflowRule[]>([]);
  const [executions, setExecutions] = useState<WorkflowExecution[]>([]);
  const [kbArticles, setKbArticles] = useState<KbArticle[]>([]);
  const [steps, setSteps] = useState<WorkflowStep[]>([]);
  const [selectedExecutionId, setSelectedExecutionId] = useState("");
  const [ruleName, setRuleName] = useState("Critical mention escalation");
  const [conditionField, setConditionField] = useState("urgency");
  const [conditionOperator, setConditionOperator] = useState("EQUALS");
  const [conditionValue, setConditionValue] = useState("CRITICAL");
  const [actionType, setActionType] = useState<ActionType>("escalate");
  const [actionPayloadJson, setActionPayloadJson] = useState('{"priority":"P1","urgency":"CRITICAL"}');
  const [mentionId, setMentionId] = useState("");
  const [dryRunOutput, setDryRunOutput] = useState<unknown>(null);
  const [executeOutput, setExecuteOutput] = useState<unknown>(null);
  const [selectedKbId, setSelectedKbId] = useState("");
  const [deletingRuleId, setDeletingRuleId] = useState<string | null>(null);
  const [pendingDeleteRule, setPendingDeleteRule] = useState<{ id: string; name: string } | null>(null);

  const refresh = useCallback(async () => {
    const [r, e, kb] = await Promise.all([
      listWorkflowRules(),
      listWorkflowExecutions(20),
      listKbArticles(),
    ]);
    setRules(Array.isArray(r) ? (r as WorkflowRule[]) : []);
    setExecutions(Array.isArray(e) ? (e as WorkflowExecution[]) : []);
    setKbArticles(Array.isArray(kb) ? (kb as KbArticle[]) : []);
    if (!mentionId && mentions.length) setMentionId(mentions[0].id);
    if (!selectedKbId && (kb || []).length) setSelectedKbId(kb[0].id);
  }, [mentionId, mentions, selectedKbId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (actionType === "escalate") setActionPayloadJson('{"priority":"P1","urgency":"CRITICAL"}');
    if (actionType === "assign") setActionPayloadJson('{"team":"FRAUD_DESK"}');
    if (actionType === "notify_webhook") {
      setActionPayloadJson('{"url":"http://localhost:9000/workflow-hook","maxAttempts":2,"timeoutMs":1200}');
    }
    if (actionType === "attach_kb_article") setActionPayloadJson('{"articleId":""}');
  }, [actionType]);

  const parsePayload = () => {
    if (actionType === "attach_kb_article") {
      return { articleId: selectedKbId || "" };
    }
    try {
      const parsed = JSON.parse(actionPayloadJson || "{}");
      if (!parsed || typeof parsed !== "object") return {};
      if (actionType === "notify_webhook") {
        const payload = parsed as Record<string, unknown>;
        const url = String(payload.url || "").trim();
        if (!url) throw new Error("notify_webhook requires a non-empty url");
        if (!/^https?:\/\//i.test(url)) throw new Error("notify_webhook url must start with http:// or https://");
      }
      return parsed as Record<string, unknown>;
    } catch (e: unknown) {
      if (e instanceof Error) throw e;
      throw new Error("Action payload must be valid JSON");
    }
  };

  const onCreateRule = async () => {
    if (!canAnalyze) return;
    try {
      const payload = {
        name: ruleName.trim(),
        priority: 10,
        conditions: [
          {
            field: conditionField,
            operator: conditionOperator,
            value: conditionValue,
            position: 1,
          },
        ],
        actions: [{ type: actionType, payload: parsePayload(), position: 1 }],
      };
      const r = await createWorkflowRule(payload);
      if (r.ok) {
        addToast({ type: "success", title: "Rule created", message: payload.name });
        await refresh();
      } else {
        addToast({ type: "error", title: "Create failed", message: "Could not create rule" });
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Invalid input";
      addToast({ type: "error", title: "Invalid payload", message: msg });
    }
  };

  const onToggleRule = async (id: string, enabled: boolean) => {
    const r = await setWorkflowRuleEnabled(id, enabled);
    if (r.ok) {
      addToast({ type: "info", title: "Rule updated", message: enabled ? "Enabled" : "Disabled" });
      refresh();
    }
  };

  const onDeleteRule = async (id: string, name: string) => {
    if (!isAdmin) return;
    setPendingDeleteRule({ id, name });
  };

  const onConfirmDeleteRule = async () => {
    if (!pendingDeleteRule) return;
    setDeletingRuleId(pendingDeleteRule.id);
    try {
      const r = await deleteWorkflowRule(pendingDeleteRule.id);
      if (r.ok) {
        addToast({ type: "success", title: "Rule deleted", message: pendingDeleteRule.name });
        await refresh();
      } else if (r.status === 401 || r.status === 403) {
        addToast({ type: "error", title: "Admin access required", message: "Only admins can delete workflow rules" });
      } else {
        addToast({ type: "error", title: "Delete failed", message: "Could not delete rule" });
      }
    } catch {
      addToast({ type: "error", title: "Delete failed", message: "Could not delete rule" });
    } finally {
      setDeletingRuleId(null);
      setPendingDeleteRule(null);
    }
  };

  const onDryRun = async () => {
    if (!mentionId) return;
    const r = await dryRunWorkflow(mentionId);
    if (r.ok) setDryRunOutput(await r.json());
  };

  const onExecute = async () => {
    if (!mentionId || !canAnalyze) return;
    const r = await executeWorkflow(mentionId);
    if (r.ok) {
      setExecuteOutput(await r.json());
      addToast({ type: "success", title: "Workflow executed", message: mentionId });
      refresh();
    }
  };

  const onLoadSteps = async () => {
    if (!selectedExecutionId) return;
    setSteps(await listWorkflowExecutionSteps(selectedExecutionId));
  };

  return (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "1.1fr .9fr", gap: 12 }}>
        <div style={{ display: "grid", gap: 12 }}>
          <div style={{ background: "var(--card-bg)", border: "1px solid var(--border)", borderRadius: 10, padding: 12 }}>
            <div style={{ color: "var(--text)", fontWeight: 600, fontSize: 12, marginBottom: 10 }}>⚙️ Create Workflow Rule</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <input
                value={ruleName}
                onChange={(e) => setRuleName(e.target.value)}
                placeholder="Rule name"
                style={{ gridColumn: "1 / span 2", background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}
              />
              <select value={conditionField} onChange={(e) => setConditionField(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}>
                <option value="urgency">urgency</option>
                <option value="sentiment">sentiment</option>
                <option value="platform">platform</option>
                <option value="topic">topic</option>
                <option value="authorFollowers">authorFollowers</option>
              </select>
              <select value={conditionOperator} onChange={(e) => setConditionOperator(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}>
                <option value="EQUALS">EQUALS</option>
                <option value="CONTAINS">CONTAINS</option>
                <option value="GTE">GTE</option>
                <option value="LTE">LTE</option>
              </select>
              <input
                value={conditionValue}
                onChange={(e) => setConditionValue(e.target.value)}
                placeholder="Condition value"
                style={{ gridColumn: "1 / span 2", background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}
              />
              <select value={actionType} onChange={(e) => setActionType(e.target.value as ActionType)} style={{ gridColumn: "1 / span 2", background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}>
                <option value="escalate">escalate</option>
                <option value="assign">assign</option>
                <option value="notify_webhook">notify_webhook</option>
                <option value="attach_kb_article">attach_kb_article</option>
              </select>
              {actionType === "attach_kb_article" ? (
                <select value={selectedKbId} onChange={(e) => setSelectedKbId(e.target.value)} style={{ gridColumn: "1 / span 2", background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12 }}>
                  {kbArticles.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.title}
                    </option>
                  ))}
                </select>
              ) : (
                <textarea
                  value={actionPayloadJson}
                  onChange={(e) => setActionPayloadJson(e.target.value)}
                  rows={3}
                  style={{ gridColumn: "1 / span 2", background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 11, fontFamily: "JetBrains Mono" }}
                />
              )}
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
              <button onClick={onCreateRule} disabled={!canAnalyze} style={{ background: canAnalyze ? C.blue + "22" : "var(--border)", color: canAnalyze ? C.blue : "var(--dim)", border: "1px solid " + (canAnalyze ? C.blue + "44" : "var(--border)"), borderRadius: 8, padding: "7px 10px", fontSize: 11, cursor: canAnalyze ? "pointer" : "not-allowed" }}>
                Create Rule
              </button>
              <button onClick={refresh} style={{ background: "none", color: "var(--muted)", border: "1px solid var(--border)", borderRadius: 8, padding: "7px 10px", fontSize: 11, cursor: "pointer" }}>
                Refresh
              </button>
            </div>
          </div>

          <div style={{ background: "var(--card-bg)", border: "1px solid var(--border)", borderRadius: 10, padding: 12 }}>
            <div style={{ color: "var(--text)", fontWeight: 600, fontSize: 12, marginBottom: 10 }}>🧪 Dry-Run / Execute</div>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" as const }}>
              <select value={mentionId} onChange={(e) => setMentionId(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "8px 10px", borderRadius: 8, fontSize: 12, minWidth: 220 }}>
                <option value="">Select mention</option>
                {mentions.slice(0, 50).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.id} · @{m.authorUsername}
                  </option>
                ))}
              </select>
              <button onClick={onDryRun} disabled={!mentionId} style={{ background: mentionId ? C.orange + "22" : "var(--border)", color: mentionId ? C.orange : "var(--dim)", border: "1px solid " + (mentionId ? C.orange + "44" : "var(--border)"), borderRadius: 8, padding: "7px 10px", fontSize: 11, cursor: mentionId ? "pointer" : "not-allowed" }}>
                Dry-run
              </button>
              <button onClick={onExecute} disabled={!mentionId || !canAnalyze} style={{ background: mentionId && canAnalyze ? C.green + "22" : "var(--border)", color: mentionId && canAnalyze ? C.green : "var(--dim)", border: "1px solid " + (mentionId && canAnalyze ? C.green + "44" : "var(--border)"), borderRadius: 8, padding: "7px 10px", fontSize: 11, cursor: mentionId && canAnalyze ? "pointer" : "not-allowed" }}>
                Execute
              </button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 10 }}>
              <pre style={{ margin: 0, background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 8, padding: 8, fontSize: 10, color: "var(--text2)", maxHeight: 200, overflow: "auto" }}>{dryRunOutput ? JSON.stringify(dryRunOutput, null, 2) : "Dry-run output"}</pre>
              <pre style={{ margin: 0, background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 8, padding: 8, fontSize: 10, color: "var(--text2)", maxHeight: 200, overflow: "auto" }}>{executeOutput ? JSON.stringify(executeOutput, null, 2) : "Execute output"}</pre>
            </div>
          </div>
        </div>

        <div style={{ display: "grid", gap: 12 }}>
          <div style={{ background: "var(--card-bg)", border: "1px solid var(--border)", borderRadius: 10, padding: 12, maxHeight: 300, overflow: "auto" }}>
            <div style={{ color: "var(--text)", fontWeight: 600, fontSize: 12, marginBottom: 8 }}>📜 Rules ({rules.length})</div>
            {rules.map((r) => (
              <div key={r.id} style={{ border: "1px solid var(--border)", borderRadius: 8, padding: 8, marginBottom: 8 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
                  <div style={{ fontSize: 11, color: "var(--text)", fontWeight: 600 }}>{r.name}</div>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <button onClick={() => onToggleRule(r.id, !r.enabled)} style={{ background: r.enabled ? C.green + "22" : C.orange + "22", color: r.enabled ? C.green : C.orange, border: "1px solid " + (r.enabled ? C.green + "44" : C.orange + "44"), borderRadius: 6, padding: "3px 7px", fontSize: 10, cursor: "pointer" }}>
                      {r.enabled ? "Enabled" : "Disabled"}
                    </button>
                    {isAdmin && (
                      <button
                        onClick={() => onDeleteRule(r.id, r.name)}
                        disabled={deletingRuleId === r.id}
                        style={{
                          background: "#ef444422",
                          color: "#ef4444",
                          border: "1px solid #ef444444",
                          borderRadius: 6,
                          padding: "3px 7px",
                          fontSize: 10,
                          cursor: deletingRuleId === r.id ? "not-allowed" : "pointer",
                        }}
                      >
                        {deletingRuleId === r.id ? "Deleting..." : "Delete"}
                      </button>
                    )}
                  </div>
                </div>
                <div style={{ marginTop: 5, color: "var(--dim)", fontSize: 10 }}>
                  priority {r.priority} · {r.conditions?.length || 0} conditions · {r.actions?.length || 0} actions
                </div>
              </div>
            ))}
            {!rules.length && <div style={{ color: "var(--dim)", fontSize: 10 }}>No workflow rules yet.</div>}
          </div>

          <div style={{ background: "var(--card-bg)", border: "1px solid var(--border)", borderRadius: 10, padding: 12, maxHeight: 360, overflow: "auto" }}>
            <div style={{ color: "var(--text)", fontWeight: 600, fontSize: 12, marginBottom: 8 }}>🧾 Execution Audit</div>
            <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
              <select value={selectedExecutionId} onChange={(e) => setSelectedExecutionId(e.target.value)} style={{ flex: 1, background: "var(--bg)", border: "1px solid var(--border)", color: "var(--text)", padding: "7px 8px", borderRadius: 8, fontSize: 11 }}>
                <option value="">Select execution</option>
                {executions.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.id} · {e.status} · {e.mentionId}
                  </option>
                ))}
              </select>
              <button onClick={onLoadSteps} disabled={!selectedExecutionId} style={{ background: selectedExecutionId ? C.blue + "22" : "var(--border)", color: selectedExecutionId ? C.blue : "var(--dim)", border: "1px solid " + (selectedExecutionId ? C.blue + "44" : "var(--border)"), borderRadius: 8, padding: "7px 10px", fontSize: 10, cursor: selectedExecutionId ? "pointer" : "not-allowed" }}>
                Load steps
              </button>
            </div>
            <pre style={{ margin: 0, background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 8, padding: 8, fontSize: 10, color: "var(--text2)", maxHeight: 240, overflow: "auto" }}>{steps.length ? JSON.stringify(steps, null, 2) : "Execution steps appear here"}</pre>
          </div>
        </div>
      </div>

      {pendingDeleteRule && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0, 0, 0, 0.45)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 999,
            padding: 16,
          }}
        >
          <div
            style={{
              width: "min(420px, 100%)",
              background: "var(--card-bg)",
              border: "1px solid var(--border)",
              borderRadius: 10,
              padding: 14,
              boxShadow: "0 10px 30px rgba(0, 0, 0, 0.35)",
            }}
          >
            <div style={{ color: "var(--text)", fontWeight: 600, fontSize: 13 }}>Delete workflow rule?</div>
            <div style={{ color: "var(--text2)", fontSize: 11, marginTop: 8 }}>
              This will permanently delete <strong>{pendingDeleteRule.name}</strong>.
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
              <button
                onClick={() => setPendingDeleteRule(null)}
                disabled={deletingRuleId === pendingDeleteRule.id}
                style={{
                  background: "none",
                  color: "var(--muted)",
                  border: "1px solid var(--border)",
                  borderRadius: 8,
                  padding: "7px 10px",
                  fontSize: 11,
                  cursor: deletingRuleId === pendingDeleteRule.id ? "not-allowed" : "pointer",
                }}
              >
                Cancel
              </button>
              <button
                onClick={onConfirmDeleteRule}
                disabled={deletingRuleId === pendingDeleteRule.id}
                style={{
                  background: "#ef444422",
                  color: "#ef4444",
                  border: "1px solid #ef444444",
                  borderRadius: 8,
                  padding: "7px 10px",
                  fontSize: 11,
                  cursor: deletingRuleId === pendingDeleteRule.id ? "not-allowed" : "pointer",
                }}
              >
                {deletingRuleId === pendingDeleteRule.id ? "Deleting..." : "Delete Rule"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

