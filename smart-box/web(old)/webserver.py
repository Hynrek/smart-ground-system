import socket
import machine
from hardware import led


def handle_request(request):
    """Wertet eine HTTP-Anfrage aus und steuert GPIO oder LED.
    Gibt eine Antwortzeichenkette zurück, oder None wenn keine bekannte Route."""
    if '/led?' in request:
        params = request.split('/led?')[1].split(' ')[0]
        state = dict(p.split('=') for p in params.split('&')).get('state', '')
        if state == 'on':
            led.on()
            return "LED aktiviert"
        elif state == 'off':
            led.off()
            return "LED deaktiviert"

    if '/gpio?' in request:
        params = request.split('/gpio?')[1].split(' ')[0]
        data = dict(p.split('=') for p in params.split('&'))
        pin_num = int(data.get('pin', 0))
        duration = int(data.get('duration', 1))

        print('pin_num', pin_num)
        print('duration', duration)

        pin = machine.Pin(pin_num, machine.Pin.OUT)
        pin.on()
        import time
        time.sleep(duration)
        pin.off()
        return "GPIO{} für {}s aktiviert".format(pin_num, duration)

    return None


def create_control_server():
    """Richtet den HTTP-Control-Server ein und gibt eine nicht-blockierende
    handle_pending()-Funktion zurück, die in der Hauptschleife aufgerufen wird."""
    with open('control.html', 'r') as f:
        html = f.read()

    addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
    s = socket.socket()
    s.setblocking(False)
    s.bind(addr)
    s.listen(1)

    print("Control-Server gestartet auf Port 80")

    def handle_pending():
        """Verarbeitet eine ausstehende HTTP-Anfrage ohne zu blockieren."""
        try:
            cl, addr = s.accept()
            try:
                request = cl.recv(1024).decode('utf-8')
                response = handle_request(request)
                if response:
                    cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + response)
                else:
                    cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html)
            finally:
                cl.close()
        except OSError:
            pass  # Kein Client verbunden — normal im nicht-blockierenden Modus

    return handle_pending
