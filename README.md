# AppPureData
<hr>
<div align="center">
 SOLO ANDROID 

 NO IOS 
</div>
<hr>
Aplicación de android hecha para pasar datos a Pure Data mediante bluetooth.

El archivo llamado "app-debug.apk" es el que tienen que tocar para comenzar la instalación de nuestra app.

Dentro de la carpeta AppPureData se encuentran todos los archivos que hacen a la app, no hace falta q los tengan en el teléfono, con instalar la app ya funciona.

<hr>
<p align="center"><b><font size="6">PASOS PARA UTILIZAR LA APP</font></b></p>

1- Instalar la app tocando el archivo "app-debug.apk"

2- Descargar el archivo "ProyectoD3.pd"

3- PUERTOS COM:
<p align="center"><b><font size="5">WINDOWS</font></b></p>
  a- Clic derecho en Inicio → Configuración → Bluetooth y dispositivos → Dispositivos → clic en Más opciones de Bluetooth.
  
  b- Se abre una ventana chica → andá a la pestaña Puertos COM.
  
  Ahí vas a ver los puertos que ya existen, con su número (COM3, COM5, etc.) y si son Entrante o Saliente, si te aparece un puerto COM ENTRANTE recuerda el número que tiene asignado y salta hasta el paso 4.
  
  <img width="426" height="503" alt="image" src="https://github.com/user-attachments/assets/12f1eb29-ace0-4a13-a939-815301f4064f" />
  
  
  c- Si no hay ninguno Entrante, hacé clic en Agregar...
  
  <img width="492" height="359" alt="image" src="https://github.com/user-attachments/assets/4e159aa9-c703-4daa-be16-9e42c0466752" />
  
  d- Elegí la opción Entrante (Incoming) → Aceptar.
  
  e- Windows te va a asignar un número de puerto (ej. COM3). Ese es el que usás en PD.

<p align="center"><b><font size="5">MAC</font></b></p>
  a- Abrí Terminal.
  Ejecutá:
  
     ls /dev/tty.* /dev/cu.*
  
  b- Buscá en el listado uno con el nombre de tu teléfono (ej. /dev/tty.MiCelular-SPP).
  Si tu teléfono ya está emparejado por Bluetooth y soporta SPP, ese dispositivo se crea solo.
  Si no aparece nada, verificá que el teléfono esté emparejado desde Preferencias del Sistema/Ajustes → Bluetooth, y volvé a correr el comando ls.
  
  d- Entrá a PureData y hacé clic en el mensaje "devices", en la consola de Pure Data te va a listar todos los puertos serie disponibles, cada uno con un número de índice:
  
     0: /dev/tty.Bluetooth-Incoming-Port
     1: /dev/tty.MiCelular-SPP
     2: /dev/cu.MiCelular-SPP
  
  e- El número de indice es por el que tenés que reemplazar el "#" en PureData

<hr>

4- Emparejá tu dispositivo android a tu PC por bluetooth

5- Abrir la App y tocar el switch de la barra de arriba

<img width="340" height="205" alt="image" src="https://github.com/user-attachments/assets/e117498f-1be6-470b-bbad-fca037d91831" />  

6- Seleccionar tu PC de la lista de dispositivos

<img width="360" height="738" alt="image" src="https://github.com/user-attachments/assets/310408aa-0c58-40f2-89d5-71469ab1cac1" />

OJO, si no tenés el patch de PureData abierto con el comport puesto NO FUNCIONA

7- Si hiciste todo bien el switch se pondrá verde y saldrá un mensaje diciendo "Dispositivo conectado"

<img width="340" height="205" alt="image" src="https://github.com/user-attachments/assets/0b269b3d-a6c8-4b4b-b20f-6993066e66b2" />

<hr>
<p align="center"><b><font size="5">¡DISFRUTAR!</font></b></p>
<hr>


