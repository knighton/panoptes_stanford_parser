do_ejml:
	cd ejml; ant
	cp ejml/build/jar/EJML.jar sp/

do_sp:
	cd sp; ant -logger org.apache.tools.ant.listener.AnsiColorLogger
	cd sp/classes; jar cf stanford-parser.jar edu/stanford/nlp/*/*.class edu/stanford/nlp/*/*/*.class edu/stanford/nlp/*/*/*/*.class
	mv sp/classes/stanford-parser.jar .

all: do_ejml do_sp

clean:
	rm -rf ejml/build/
	rm -rf sp/classes/
	rm -f sp/EJML.jar stanford-parser.jar

run_server:
	./run_server.sh 1337 data/englishPCFG.ser.gz
