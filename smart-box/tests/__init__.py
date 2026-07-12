import os
import sys

# Repo-Wurzel (smart-box/) auf den Pfad, damit `import hardware` etc. funktioniert
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

# Fakes installieren, BEVOR Firmware-Module importiert werden
from . import _stubs  # noqa: E402,F401
