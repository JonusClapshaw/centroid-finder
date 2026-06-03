# Priority Improvements

## refactoring code (required)
1. Refactor recursive flood fill in DfsBinaryGroupFinder.java to an iterative traversal to reduce stack overflow risk on large connected components. 
2. Remove/refactor dead and redundant structure (unused dfs parameters, duplicate imports, and legacy pathways like ImageSummaryApp flow overlap) to simplify maintenance. (Implementing)

## adding tests (required)
1. Fix and stabilize video fixture strategy in VideoFrameReader tests so CI does not depend on missing local files (ensantina path mismatch).
2. Add high-value edge-case tests for input validation and boundary conditions in CsvWriter, DistanceImageBinarizer, and DfsBinaryGroupFinder (empty arrays, malformed input, no centroid cases).

## improving error handling (required)
1. Strengthen VideoProcessorApp argument validation (blank paths, negative threshold, invalid target color format) with precise IllegalArgumentException messages. (Implementing)
2. Replace broad/legacy catch-and-print patterns with typed exception handling and consistent user-facing error surfaces across image and video entry points.

## writing documentation (required)
1. Resolve contract/documentation mismatch for group ordering rules (BinaryGroupFinder docs vs Group.compareTo behavior) and document one canonical ordering.
2. Update README run/validation instructions to match the current video pipeline, server routes, and packaging flow.

## bug fixes (required)
1. Align .gitignore behavior with tracked build artifacts to stop target/surefire output churn and reduce noisy diffs.
2. Fix edge-case runtime failures caused by contract gaps (empty binary arrays, inconsistent validation assumptions across classes). (Implementing)

## hardening security (required)
1. Add stricter path and filename validation at API boundaries to prevent traversal-style misuse and unsafe file access.
2. Add input and workload limits for processing requests (validation, size/time limits, and request throttling strategy).

## improving performance (optional)
1. Optimize color distance hot path by reducing expensive per-pixel math in EuclideanColorDistance and binarization loops.
2. Profile long-video processing and improve frame sampling/processing throughput for large inputs.

## other (optional)
1. Standardize output determinism (CSV locale and charset handling) to avoid environment-specific formatting bugs.
2. Audit dependencies (for example jave-all-deps necessity) and reduce unused packages to lower maintenance overhead.
