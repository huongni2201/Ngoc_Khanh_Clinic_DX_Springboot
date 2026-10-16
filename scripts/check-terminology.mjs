import { readFile } from 'node:fs/promises';

const checks = [
  {
    file: 'CONTEXT.md',
    needle: 'Participant retained as the business term',
  },
  {
    file: 'docs/adr/0013-clean-slate-application-contract.md',
    needle: '**Participant** terminology',
  },
];

const failures = [];

for (const check of checks) {
  try {
    const content = await readFile(check.file, 'utf8');
    if (!content.includes(check.needle)) {
      failures.push(
        `Expected "${check.needle}" in ${check.file}, but it was not found.`,
      );
    }
  } catch (error) {
    failures.push(`Unable to read ${check.file}: ${error.message}`);
  }
}

if (failures.length > 0) {
  console.error('Terminology check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('Terminology check passed.');
