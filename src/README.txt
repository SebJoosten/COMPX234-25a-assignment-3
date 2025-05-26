I've tested it at home with a 700MB video file and checked the hash and size of the original file VS the moved one
Everything matched up. 
You can close and re open the client at any point or stop communication and it will just work it out. 
if the client gets bad arguments or calls the server and the file doesn't exists it will just close.  
The resume function was tested too and worked fine even with mutable clients at the same time.
The client has a safer mode that rechecks the file size before 

The server had a delay function commented out at the bottom to test of the clients will update there timeouts. 
The clients will increment by 1000ms for every timeout and decrease by 10ms for every successful packet.

Everything seems to work fine at my end if there are problems just ask me

The folder Server_files - is where you put the files to download 
The folder Client output is where you will find them after they are run 

The server will check the Sever_files folder every time you make a download request

Client arguments - localhost 51234 "test.png"
Server arguments - 51234


Test on a lab machine 
110ba2d01721d726d37531921017837b - md5sum from server side
110ba2d01721d726d37531921017837b - md5sum from client side 

Client 2 just has a different path for testing they both work at the same time and will restart when interrupted and with no duplicate file paths or other arguments