package com.damxat.damxatm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class ClientXat extends Thread {

    private Socket socket;
    private BufferedReader br;
    private BufferedWriter bw;
    private String host;
    private int puerto;
    private String nick;

    //el client se crea con la ip del servidor y el puerto al que se conectará
    public ClientXat(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    @Override
    public void run() {
        try {
            //crear socket
            //indica la peticion de conexion
            this.socket = new Socket(this.host, this.puerto);
            System.out.println("[Client] Connectat al servidor " + this.host + ":" + this.puerto);
            

            // crea los br y bw para poder escribir y recibir mensajes
            //los streams son para enviar y recibir datos, solo incluye una palabra, los buffered es para leer varias palabras
            this.br = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.bw = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            
            //selecciona nick
            if (!seleccionaNick()) return;

            // HILO SECUNDARIO hilo receptor de mensajes del servidor
            Thread receptor = new Thread(() -> recibeMensajes());
            //un daemon es un hilo que muere cuando los hilos principales mueren, son join() ni cosas dde esas
            //jvm solo termina cuando los hilos principales han terminado
            //el hilo se convierte a daemon porque no tiene sentido que el servidor este escuchando mensajes si se ha apagado
            //si no fuera un daemon la jvm no podria cerrarse pues siempre está escuchando a ver si recibe un mensaje
            receptor.setDaemon(true);
            //hay que hacerlo daemon antes de inicializarlo
            receptor.start();

            //System.out.print("Introdueix el teu nick:");
            //seleccionaNick();
            
            //HILO PRINCIPAL enviar mensajes
            //se va repitiendo porque dentro de esa funcion hay un while
            enviarMensaje();

        } catch (IOException e) {
            System.out.println("[Client] No s'ha pogut connectar: " + e.getMessage());
        }
    }


    private void enviarMensaje() {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        String linea;
        System.out.println("Escriu els teus missatges (escriu 'adeu' per sortir):");
        try {
            //mientras la linea tenga algo escrito
            while ((linea = teclado.readLine()) != null) {
                //escribe la linea en el buffer
                this.bw.write(linea);
                //escribe un separador \n
                this.bw.newLine();
                //flush escribe los bytes almacenados en buffer y lo limpia
                this.bw.flush();
                //si es adeu o exit, sale
                if (linea.equalsIgnoreCase("adeu") || linea.equalsIgnoreCase("exit")) {
                    cerrar();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            System.out.println("[Client] Error llegint del teclat.");
            System.out.println(e.getStackTrace());
            System.out.println(e.getMessage());
        }
    }
    
    //revisar esto
    private void recibeMensajes() {
        String mensaje;
        try {
            //mientras la linea tenga algo escrito
            while ((mensaje = this.br.readLine()) != null) {
                //si es adeu o exit
                if (mensaje.equalsIgnoreCase("adeu") || mensaje.equalsIgnoreCase("exit")) {
                    //desconecta el server
                    System.out.println("[Servidor] " + mensaje);
                    System.out.println("[Client] Servidor desconnectat.");
                    //cierra todo llamandp a la funcion y termina el programa
                    cerrar();
                    System.exit(0);
                }
                //si no se ha cerrado, envia mensaje
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            //si hay error se cierra el server
            System.out.println("[Client] Connexió tancada.");
        }
    }

    public boolean seleccionaNick() {
        //abbre br 
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        try {
            
            String nick = "";
            while (true) {
                
                System.out.print("Introdueix el teu nick: ");
                nick = teclado.readLine();
                
                if (nick == null || nick.isBlank()) {
                    System.out.println("El nick no pot estar buit.");
                    continue;
                }
                
                //envia nick al server, es como enviar un mensaje
                this.bw.write(nick);
                this.bw.newLine();
                this.bw.flush();

                //espera respuesta del servidor
                String resposta = this.br.readLine();
                
                if ("NICK_OK".equals(resposta)) {
                    
                    System.out.println("Benvingut, " + nick + "!");
                    this.nick = nick;
                    return true;
                    
                } else if ("NICK_REPETIT".equals(resposta)) {
                    
                    System.out.println("Aquest nick ja està en ús. Tria'n un altre.");
                    
                } else {
                    
                    System.out.println("Error del servidor: " + resposta);
                    return false;
                    
                }
            }
        } catch (IOException e) {
            System.out.println("[Client] Error seleccionant nick.");
            return false;
        }
    }

    public void cerrar() {
        try {
            if (this.br != null) this.br.close();
            if (this.socket != null) this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}