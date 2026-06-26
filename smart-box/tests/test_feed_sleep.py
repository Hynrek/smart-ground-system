from tests import _stubs
import unittest
import hardware


class FeedSleepTest(unittest.TestCase):
    def setUp(self):
        _stubs.clock.reset()

    def test_chunks_and_feeds(self):
        wdt = _stubs.machine.WDT(timeout=8000)
        hardware.feed_sleep_ms(10000, wdt)            # 4000 + 4000 + 2000
        self.assertEqual(wdt.fed, 3)
        self.assertEqual(_stubs.clock.ms, 10000)

    def test_no_wdt_just_sleeps(self):
        hardware.feed_sleep_ms(5000, None)
        self.assertEqual(_stubs.clock.ms, 5000)

    def test_short_sleep_single_chunk(self):
        wdt = _stubs.machine.WDT()
        hardware.feed_sleep_ms(1000, wdt)
        self.assertEqual(wdt.fed, 1)
        self.assertEqual(_stubs.clock.ms, 1000)


if __name__ == "__main__":
    unittest.main()
