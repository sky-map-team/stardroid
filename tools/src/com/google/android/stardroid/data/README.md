The following procedure will regenerate the binary star data:
1  Build Sky Map with `ant release` to ensure the relevant
   class files are available. 
1  Use the proto.sh script to create the "lite" version of the protocol
   buffers and then convert the CSV, KML, etc data files into _R.ascii
   protocol buffers (if you don't need the "lite" version of the protos,
   you can use the "proto_generate.sh" script instead)
1  Then use rewrite.sh to replace all the R constants with their integer
   values (from R.java).
1  Finally use the binary.sh to convert the ascii proto bufs to binary ones
   (and put them in the right directory).
