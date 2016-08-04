#!/usr/bin/env python
import SimpleHTTPServer
import SocketServer
import subprocess
from urlparse import urlparse, parse_qs


class AnswerRequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):

    # TODO: Consider loading the NHS data only once when the server starts.
    #       As of now, the data has to be loaded again at each request.

    def do_GET(self):
        request = urlparse(self.path)
        if request.path == '/answer':
            # Parse the query
            query = parse_qs(request.query)["q"][0]

            # Call the Java implementation of QuestionAnswerer
            output = subprocess.Popen(
                        [('java -Dfile.encoding=UTF-8 -classpath ' +
                          '"bin:lib/jsoup-1.9.2.jar:lib/json-simple-1.1.1.jar:lib/stanford-corenlp.jar" ' +
                          'com.mikhail_dubov.nhs.QuestionAnswerer data/data.json data/stopwords.txt "%s"'
                          % query)],
                        stdout=subprocess.PIPE, shell=True).communicate()[0]

            # Send the result back in JSON format
            self.send_response(200)
            self.send_header("Content-type", "text/json")
            self.send_header("Content-length", len(output))
            self.end_headers()
            self.wfile.write(output)


server = SocketServer.TCPServer(("", 8080), AnswerRequestHandler)
server.serve_forever()
