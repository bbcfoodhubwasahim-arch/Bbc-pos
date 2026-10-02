import os
import sys
from http.server import HTTPServer, SimpleHTTPRequestHandler, ThreadingHTTPServer

class RangeRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory="/app/applet/public", **kwargs)

    def end_headers(self):
        self.send_header('Accept-Ranges', 'bytes')
        super().end_headers()

if __name__ == '__main__':
    port = 3000
    server_address = ('0.0.0.0', port)
    httpd = ThreadingHTTPServer(server_address, RangeRequestHandler)
    print(f"Threading HTTP Server running on port {port}...")
    httpd.serve_forever()
