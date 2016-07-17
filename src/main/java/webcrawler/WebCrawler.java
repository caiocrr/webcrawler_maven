/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package webcrawler;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.remote.ErrorHandler;

/**
 *
 * @author Caio
 */
public class WebCrawler {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
//
        WebCrawler crawler = new WebCrawler();
//        crawler.pacheco();
        crawler.farmagora();
    }

    public void farmagora() {
        Connection conn = getConnection();

        for (int j = 1; j <= 229; j++) {
            String URL = "http://www.farmagora.com.br/departamento/1//0/pag" + j;

            Document doc;
            try {
                org.jsoup.Connection conn2 = Jsoup.connect(URL);
                conn2.userAgent("Mozilla/5.0");

                doc = conn2.get();
                Elements all = doc.select("div#lista.listagem2.listagem");
                Elements produtos = all.select("ul");

                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
                for (int i = 0; i < produtos.size(); i++) {
                    Elements li = produtos.get(i).select("li.dadosProduto");
                    Elements nome = li.select("a");

                    Elements preco = produtos.get(i).select("div.parcela");

                    if (insertDB(nome.get(0).text(), nf.parse(preco.get(0).text().split(" ")[1]).floatValue(), "Farmagora", conn)) {
                        System.out.println("Inserido " + nome.get(0).text() + "  -  " + nf.parse(preco.get(0).text().split(" ")[1]).floatValue());
                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (ParseException ex) {
//                Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
//            }

            } catch (ParseException ex) {
                Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    public void ultrafarma() {
        Connection conn = getConnection();

        for (int j = 362; j <= 561; j++) {
            String URL = "http://www.ultrafarma.com.br/categoria-372/ordem-1/pagina-" + j + "/Medicamentos.html";

            Document doc;
            try {
                doc = Jsoup.connect(URL).get();
                Elements produto = doc.select("div.conjuto_produtos_categorias");
                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
                for (int i = 0; i < produto.size(); i++) {

                    Elements nome = produto.select("a.lista_produtos");
                    Elements preco = produto.select("div.txt_por");

                    if (insertDB(nome.get(i).text(), nf.parse(preco.get(i).text().split(" ")[2]).floatValue(), "Ultra Farma", conn)) {
                        System.out.println("Inserido " + nome.get(i).text());
                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParseException ex) {
                Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public void pacheco() {
        Connection conn = getConnection();
        Document doc;

        for (int j = 1; j <= 375; j++) {
            String URL = "http://www.drogariaspacheco.com.br/medicamentos#" + j;
            JBrowserDriver driver = new JBrowserDriver(Settings.builder().
                    timezone(Timezone.AMERICA_LIMA).build());

            System.out.println(URL + " (...)");
            driver.get(URL);

            doc = Jsoup.parse(driver.getPageSource());

            Elements medicamentos = doc.select("li.medicamentos");
            Elements content = medicamentos.select("a");

            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
            for (int i = 0; i < content.size(); i++) {
                String title = content.get(i).attr("title");

                Elements prices = content.get(i).select("span.bestPrice").select("span.the-price");
                if (prices.size() != 0) {
                    try {
                        if (insertDB(title, nf.parse(prices.get(0).text().substring(2).trim()).floatValue(), "Drogarias Pacheco", conn)) {
                            System.out.println("Inserido " + title);
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(WebCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }

            driver.quit();
        }

    }

    public boolean insertDB(String produto, Float price, String farmacia, Connection conn) {

        //1 - get FARMACIA ID
        String query = "SELECT id_farmacia FROM farmacias WHERE nome=?";
        try {
            PreparedStatement ppStm = conn.prepareStatement(query);
            ppStm.setString(1, farmacia);

            ResultSet rSet = ppStm.executeQuery();

            if (rSet.next()) {

                //Main Table
                int id_farmacia = rSet.getInt(1);

                String insertPreco = "INSERT into precos(nome,preco,id_farmacia) values(?,?,?) RETURNING id";
                PreparedStatement ppStm2 = conn.prepareStatement(insertPreco);
                ppStm2.setString(1, produto);
                ppStm2.setFloat(2, price);
                ppStm2.setInt(3, id_farmacia);

                ResultSet rSet2 = ppStm2.executeQuery();

                if (rSet2.next()) {
                    int idInserted = rSet2.getInt(1);

                    //Words Title Table               
                    String[] palavrasProduto = extrairPalavras(produto);
                    PreparedStatement comandoSQL = null;

                    for (String cadaPalavra : palavrasProduto) {
                        comandoSQL = conn.prepareStatement(
                                "INSERT INTO palavrasprodutonormal (palavra_produto_normal,pid) "
                                + "VALUES(?,?);");
                        comandoSQL.setString(1, cadaPalavra);
                        comandoSQL.setInt(2, idInserted);

                        comandoSQL.executeUpdate();
                    }

                }
            }
            return true;

        } catch (SQLException ex) {
            ex.printStackTrace();

        }

        return false;
    }

    public static String convertUTF8toISO(String str) {
        String ret = null;
        try {
            ret = new String(str.getBytes("ISO-8859-1"), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
        return ret;
    }

    private String[] extrairPalavras(String busca) {
        busca = removeDiacriticals(busca);
        String[] temp = busca.split(" ");
        for (int i = 0; i < temp.length; i++) {
            temp[i] = temp[i].trim();
        }
        return temp;
    }

    public Connection getConnection() {
        try {

            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {

            System.out.println("Where is your PostgreSQL JDBC Driver? "
                    + "Include in your library path!");
            e.printStackTrace();
            return null;

        }
        System.out.println("PostgreSQL JDBC Driver Registered!");

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:5432/gestao", "postgres",
                    "caio2009");

        } catch (SQLException e) {

            System.out.println("Connection Failed! Check output console");
            e.printStackTrace();
            return null;

        }

        if (connection != null) {
            System.out.println("You made it, take control your database now!");
        } else {
            System.out.println("Failed to make connection!");
        }

        return connection;
    }

    public static String removeDiacriticals(String input) {
        if (input == null || input.equals("")) {
            return input;
        }
        input = input.toUpperCase();
        final String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String final2 = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        final2 = final2.replace("'", " ");
        final2 = final2.replace("´", " ");
        final2 = final2.replace("-", " ");
        final2 = final2.replace(":", "");
        return final2;
    }

}
