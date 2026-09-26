import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, "..");

const sourceExtensions = new Set([
  ".java",
  ".js",
  ".json",
  ".md",
  ".mjs",
  ".properties",
  ".sql",
  ".ts",
  ".tsx",
  ".xml",
  ".yml",
  ".yaml",
]);

const deprecatedPatterns = [
  { label: "FRONT_DESK", pattern: /\bFRONT_DESK\b/g },
  { label: "FrontDesk", pattern: /\bFrontDesk\b/g },
  { label: "front desk", pattern: /\bfront[\s_-]+desk\b/gi },
  { label: "DoctorWorklist", pattern: /\bDoctorWorklist\b/g },
  { label: "Doctor Worklist", pattern: /\bDoctor\s+Worklist\b/gi },
  { label: "HealthCheck", pattern: /HealthCheck[A-Za-z0-9_]*/g },
  { label: "Health Check", pattern: /\bHealth[\s_-]+Check\b/gi },
  { label: "health_check", pattern: /\bhealth_check[A-Za-z0-9_]*/g },
  { label: "health-check", pattern: /\bhealth-check[A-Za-z0-9-]*/gi },
  { label: "CLS", pattern: /\bCLS\b|\bCls\b|\bcls\b/g },
  { label: "DiagnosticResult", pattern: /\bDiagnosticResult[A-Za-z0-9_]*/g },
];

const excludedPathReasons = [
  ["docs/baseline/", "generated mirrors whose FINAL DOCX sources are unavailable"],
  ["docs/domain/MEDICAL_TERMINOLOGY.md", "glossary intentionally names legacy terms"],
  ["docs/refactor/terminology/", "inventory and migration-map documents intentionally name legacy terms"],
  ["docs/superpowers/plans/2026-09-25-medical-terminology-refactor.md", "execution plan intentionally names legacy terms"],
  ["src/test/java/com/ngockhanh/clinic/infrastructure/migration/PostgreSqlMigrationIntegrationTest.java", "migration integration test checks retired table names are absent"],
  ["AGENTS.md", "repository instructions are an existing authoritative contract"],
  ["PROJECT_RULES.md", "repository rules are an existing authoritative contract"],
  ["PROJECT_SKILLS.md", "repository skills are an existing authoritative contract"],
  ["scripts/check-terminology.mjs", "the guard must contain the patterns it checks"],
];

function normalizedRelativePath(filePath) {
  return path.relative(repositoryRoot, filePath).split(path.sep).join("/");
}

function exclusionReason(relativePath) {
  for (const [prefix] of excludedPathReasons) {
    if (relativePath === prefix || relativePath.startsWith(prefix)) {
      return "documented repository exception";
    }
  }

  return null;
}

async function collectFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (
      entry.name === ".git" ||
      entry.name === ".codegraph" ||
      entry.name === ".agents" ||
      entry.name === ".codex" ||
      entry.name === ".idea" ||
      entry.name === ".superpowers" ||
      entry.name === "node_modules" ||
      entry.name === "target" ||
      entry.name === "target-terminology"
    ) {
      continue;
    }

    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(fullPath)));
    } else if (sourceExtensions.has(path.extname(entry.name).toLowerCase())) {
      files.push(fullPath);
    }
  }

  return files;
}

function findMatches(content) {
  const matches = [];

  for (const { label, pattern } of deprecatedPatterns) {
    for (const match of content.matchAll(pattern)) {
      const offset = match.index ?? 0;
      const lineStart = content.lastIndexOf("\n", offset - 1) + 1;
      const lineNumber = content.slice(0, offset).split("\n").length;
      const columnNumber = offset - lineStart + 1;
      const lineEnd = content.indexOf("\n", offset);
      const line = content.slice(lineStart, lineEnd === -1 ? content.length : lineEnd).trim();
      matches.push({ label, lineNumber, columnNumber, line });
    }
    pattern.lastIndex = 0;
  }

  return matches.sort(
    (left, right) =>
      left.lineNumber - right.lineNumber || left.columnNumber - right.columnNumber,
  );
}

const findings = [];
const files = await collectFiles(repositoryRoot);

for (const filePath of files) {
  const relativePath = normalizedRelativePath(filePath);
  if (exclusionReason(relativePath)) {
    continue;
  }

  const content = await readFile(filePath, "utf8");
  for (const match of findMatches(content)) {
    findings.push({ relativePath, ...match });
  }
}

if (findings.length > 0) {
  console.error("Terminology guard failed with " + findings.length + " finding(s):");
  for (const finding of findings) {
    console.error(
      finding.relativePath +
        ":" +
        finding.lineNumber +
        ":" +
        finding.columnNumber +
        " [" +
        finding.label +
        "] " +
        finding.line,
    );
  }
  process.exitCode = 1;
} else {
  console.log("Terminology guard passed: no deprecated terminology in active sources.");
}
