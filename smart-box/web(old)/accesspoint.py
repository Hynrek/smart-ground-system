import network
import socket
import time
import json
import machine

USER_CONFIG = "./userconfig/client_config.json"

def start_ap():
    print('Try Activating Access Point.')
    setupConfig = None
    html = None

    try:
        with open('./systemconfig/accesspoint_config.json', 'r') as f:
            setupConfig = json.load(f)
    except (OSError, ValueError) as e:
        print("Access Point Konfiguration nicht geladen:", e)
        return

    try:
        with open('webserverui.html', 'r') as f:
            html = f.read()
    except (OSError, ValueError) as e:
        print("Access Point HTML nicht geladen:", e)
        html = "<html><body><h1>Fehler</h1></body></html>"

    if not setupConfig or not html:
        return    
    
    
    wlan = network.WLAN(network.AP_IF)
    wlan.config(essid=setupConfig['accesspoint_ssid'], password=setupConfig['accesspoint_pass'])
    wlan.active(True)
    
    print('Access Point Active.')
    print('Network Name:', setupConfig['accesspoint_ssid'])
    print('IP Address:', wlan.ifconfig()[0])
    
    addr = socket.getaddrinfo('0.0.0.0', 80)[0][-1]
    s = socket.socket()
    s.settimeout(None)
    s.bind(addr)
    s.listen(1)
    
    while True:
        cl, addr = s.accept()
        request = cl.recv(1024).decode('utf-8')
        
        if '/save?' in request:
            params = request.split('?')[1].split(' ')[0]
            data = {p.split('=')[0]: p.split('=')[1] for p in params.split('&')}
            
            with open(USER_CONFIG, 'w') as f:
                json.dump(data, f)
            
            cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nSettings Saved! Rebooting...')
            cl.close()
            time.sleep(2)
            machine.reset()
        
        cl.send('HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n' + html)
        cl.close()
