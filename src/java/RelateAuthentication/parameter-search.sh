magic1=3; while [ $magic1 -le 30 ]; do magic2=50; while [ $magic2 -le 250 ]; do echo -n "$magic1 $magic2 "; i=0; while [ $i -lt 30 ]; do time=`./runme.sh $magic1 $magic2 | awk ' /time to get dongle.s attention/ { print $7; }'`; echo -n "$time "; let i=i+1; done; echo; let magic2=magic2+25; done; let magic1=magic1+1; done | tee attention-magic-values.log