# Review instructions

## What Important means here

Reserve Important for findings that would break behavior, leak data,
or crash the app: incorrect logic, unscoped database queries, PII
in logs or error messages, and migrations that aren't backward
compatible. 
Style, naming, and refactoring suggestions are Nit at
most.

## Cap the nits

Report at most five Nits per review.

## Do not report

- Anything CI already enforces: lint, formatting, type errors
- Test-only code that intentionally violates production rules

## Always check

- Behavior in Android and iOS is the same (new features should work in both).
- For any refactoring opportunities to simplify the code that are justified following this change.
