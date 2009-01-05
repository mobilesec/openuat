<?php
/**
* Autor: Lukas Wallentin
* Returns the conted of the file "folder" seperated by "|"
* deletes messages older then 5 minutes. 
* using the parameter "time" it is possible to receive just messages which #
* where posted after the specified time 
*/

if (ereg("^[a-zA-Z0-9_]", $_GET["folder"]))
{

$starTime="0";
if ($_GET["time"]!=""){
$starTime=$_GET["time"];
}

$fp = @fopen("data/".$_GET["folder"].".txt", "r");
if(!$fp) {
  echo ""; //return empty sting if there is no such file
} else {
$data="";
  while (feof($fp) != 1)
  {
  	$data=$data.fgets($fp,1024);
  }
}
$save="";
$out="";
$dataArray = explode("\n",$data);
for ($i=0; $i<sizeof($dataArray); $i++)
{

  $part=explode("-",$dataArray[$i]);
$now=time();
$makenew=0;
  if ($part[0]>($now-300))
  {
  	$save=$save.$dataArray[$i]."\n";
  }
else
{
$makenew=1;
}
    if ($part[0]>=($starTime))
    {
    	$out=$out.$dataArray[$i]."|";
  }
}


@fclose($fp);
if ($makenew==1)
{
	@unlink("data/".$_GET["folder"].".txt");
	if ($save!="")
	{
		$fp = fopen("data/".$_GET["folder"].".txt", "w");
		fwrite($fp, $save);
		fclose($fp);
	}
}
echo $out;
//echo "<br><br>".$starTime;

}
else
{
echo "-1";
}
?>