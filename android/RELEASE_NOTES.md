# REC Remote Pad 1.0.10

- Pause latches on touch-down, including while another direction is held. Lifting the Pause finger does not resume.
- Stop frames now separate S, ! and P with CR/LF, supporting both byte and line-oriented parsing. Priority queue clearing and repeated pause stops remain enabled.
- Explicit multi-touch splitting on control containers.
- Long-press the top device name/title for completed Bluetooth writes and received robot data.
- Touch and protocol framing regression checks added. Physical stopping still depends on the robot's installed sketch, connection and motor driver; it has not been verified on the user's robot.
