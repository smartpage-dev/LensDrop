# H5.1 build contracts

Run `python3 buildfix-tests/run_buildfix_tests.py` from clients/android.

These tests compile the real DirectorySync class against deliberately small Android public-API stubs with simulated syscall success/failure. The pairing callback check projects the actual method declaration and reference into a minimal Activity contract. Negative controls recreate both reported compiler failures. These are **not** a full SDK build, JNI/filesystem durability test, or runtime pairing test.

`RUN_PRINT_POLICY_TESTS.sh` invokes them after the unchanged print-policy suite.
