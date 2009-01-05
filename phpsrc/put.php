<?php
/**
* Autor: Lukas Wallentin
* Puts message ("msg") in the File "folder" in the folder "data"
*/

if (ereg("^[a-zA-Z0-9_]", $_GET["folder"]))
{
  $fp = fopen("data/".$_GET["folder"].".txt", "a+");
  fwrite($fp, time()."-".$_GET["msg"]."\n");
  fclose($fp);
  echo "0";
}
else
{
  echo "-1";
}
?>