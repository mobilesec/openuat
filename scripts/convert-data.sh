for f in /data/shaking/data/*.log.gz; do 
  java -cp dist/openuat-distbundle.jar:lib/jfreechart-1.0.1.jar \
      org.openuat.sensors.ParallelPortPWMReader $f convert_active | gzip > \
      /data/shaking/data-simpleformat/`basename $f`
done
