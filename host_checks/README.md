Host-only behavior checks; these do not emulate AVR timers or physical components.

From this directory, run:

```sh
g++ -std=c++11 -Wall -Wextra -pedantic -I stubs firmware_test.cpp -o /tmp/rec_firmware_check
/tmp/rec_firmware_check
```
