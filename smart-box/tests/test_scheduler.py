from tests import _stubs
import unittest
import hardware


def _gpio(device_id, pin, duration=0, blocked=False):
    return {
        "device_id": device_id, "alias": "d", "device": "GPIO",
        "direction": "OUTPUT", "command": str(pin),
        "signal_duration_ms": duration, "blocked": blocked,
    }


def _led(device_id, duration=0):
    return {
        "device_id": device_id, "alias": "l", "device": "LED",
        "direction": "OUTPUT", "command": "ON",
        "signal_duration_ms": duration, "blocked": False,
    }


class SchedulerTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()
        self.gm = hardware.GpioManager()

    def _pin(self, device_id):
        return self.gm._devices[device_id]["pin"].value()

    def test_pulse_starts_and_expires(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertEqual(self._pin("a"), 1)
        _stubs.clock.advance(499)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 1)          # noch aktiv
        _stubs.clock.advance(1)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 0)          # abgelaufen
        self.assertNotIn("a", self.gm._pulse_active)

    def test_busy_reject(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertFalse(self.gm.set("a", 1))        # läuft noch -> abgelehnt
        self.assertEqual(self._pin("a"), 1)

    def test_off_cancels_pulse(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.gm.set("a", 1)
        self.assertTrue(self.gm.set("a", 0))
        self.assertEqual(self._pin("a"), 0)
        self.assertNotIn("a", self.gm._pulse_active)

    def test_no_duration_is_not_busy(self):
        self.gm.setup([_gpio("a", 15, duration=0)])
        self.assertTrue(self.gm.set("a", 1))
        self.assertEqual(self._pin("a"), 1)
        self.assertNotIn("a", self.gm._pulse_active)  # kein Puls -> nicht beschäftigt
        self.assertTrue(self.gm.set("a", 1))          # erneut erlaubt

    def test_duration_override(self):
        self.gm.setup([_gpio("a", 15, duration=100)])
        self.gm.set("a", 1, 300)                       # Override 300 ms
        _stubs.clock.advance(150)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 1)            # 150 < 300 -> noch an
        _stubs.clock.advance(160)
        self.gm.tick()
        self.assertEqual(self._pin("a"), 0)

    def test_rearm_after_expiry(self):
        self.gm.setup([_gpio("a", 15, duration=500)])
        self.gm.set("a", 1)
        _stubs.clock.advance(500)
        self.gm.tick()                        # Puls abgelaufen
        self.assertNotIn("a", self.gm._pulse_active)
        self.assertTrue(self.gm.set("a", 1))  # wieder auslösbar
        self.assertEqual(self._pin("a"), 1)

    def test_led_pulse_expires(self):
        self.gm.setup([_led("l", duration=300)])
        self.assertTrue(self.gm.set("l", 1))
        self.assertEqual(hardware.led.value(), 1)
        _stubs.clock.advance(300)
        self.gm.tick()
        self.assertEqual(hardware.led.value(), 0)
        self.assertNotIn("l", self.gm._pulse_active)

    def test_led_rejects_off(self):
        self.gm.setup([_led("l")])
        self.assertFalse(self.gm.set("l", 0))

    def test_blocked_entry_rejected(self):
        self.gm.setup([_gpio("a", 15, blocked=True)])
        self.assertFalse(self.gm.set("a", 1))

    def test_set_has_no_delay_param(self):
        import inspect
        params = list(inspect.signature(hardware.GpioManager.set).parameters)
        self.assertEqual(params, ["self", "device_id", "value", "signal_duration_ms"])


if __name__ == "__main__":
    unittest.main()
