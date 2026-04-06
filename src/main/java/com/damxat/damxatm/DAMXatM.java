package com.damxat.damxatm;

import java.util.Scanner;

public class DAMXatM {
    
    //nvegar a carpeta
    //cd "/cygdrive/e/p serveis/DAMXat/DAMXatM/src/main/java/com/damxat/damxatm"
    
    //compliar tot
    //javac -d out DAMXatM.java ServidorXat.java ClientXat.java
    
    //ejecutar programa
    //java -cp out com.damxat.damxatm.DAMXatM

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        boolean continuar = true;

        do {
            System.out.println("----Chat----");
            System.out.println();
            System.out.println("Selecciona el mode:");
            System.out.println("  1 - Servidor");
            System.out.println("  2 - Client");
            System.out.print("Opció: ");

            String opcio = sc.nextLine().trim();

            switch (opcio) {
                case "1":
                    continuar = false;
                    //escoger port
                    System.out.print("Port d'escolta [per defecte 5000]: ");
                    //trim elimina espacios
                    String portStr = sc.nextLine().trim();
                    //si esta vacio selecciona el 5000, si no, parsea el string a int
                    int port = portStr.isEmpty() ? 5000 : Integer.parseInt(portStr);

                    //inicia servidor
                    ServidorXat servidor = new ServidorXat(port);
                    servidor.start();
                    break;

                case "2":
                    continuar = false;
                    System.out.print("IP del servidor [per defecte 127.0.0.1]: ");
                    String host = sc.nextLine().trim();
                    if (host.isEmpty()) {
                        host = "127.0.0.1";
                    }

                    System.out.print("Port del servidor [per defecte 5000]: ");
                    String portClientStr = sc.nextLine().trim();
                    int portClient = portClientStr.isEmpty() ? 5000 : Integer.parseInt(portClientStr);

                    ClientXat client = new ClientXat(host, portClient);
                    client.start();
                    break;

                default:
                    System.out.println("Opció no vàlida. Torna a intentar.");

            }
        } while (!continuar);

        sc.close();
    }

}
