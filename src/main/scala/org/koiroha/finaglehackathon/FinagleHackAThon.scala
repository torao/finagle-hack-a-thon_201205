/**
 *
 */
package org.koiroha.finaglehackathon
import java.net.InetSocketAddress

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http._
import com.twitter.finagle.http.path._

object HTTPServer {

  def main(args: Array[String]) = {

    val service = new Service[Request, Response] {
      def apply(request: Request) = {
        val response = Response()

        Path(request.path) match {
          case Root / "index.html" =>
            response.contentType = "text/html;charset=UTF-8"
            response.withWriter{ out =>
              import java.io._
              val file = new File("src/main/site/index.html")
              val in = new InputStreamReader(new FileInputStream(file), "UTF-8")
              var eof = false
              while(! eof){
                val ch = in.read()
                eof = (ch < 0)
                if(! eof){
                  out.write(ch)
                }
              }
            }
          case Root / "whiteboard" => try {
        	  if(request.method == org.jboss.netty.handler.codec.http.HttpMethod.POST){
	            val msg = request.getParam("msg")
	            publish(msg)
	            System.out.println("publish: %s (%s)".format(msg, request.params))
        	  }
	    	    val after = request.getLongParam("after")
	    	    response.contentType = "text/plain;charset=UTF-8"
	    	    response.setContentString(retrieve(after))
	            System.out.println("retrieve: %d".format(after))
	        } catch {
	          case ex =>
	            ex.printStackTrace()
	        }
          case _ =>
            response.setContentString("Hello, world!")
        }

        Future.value(response)
      }
    }

    val server: Server = {
      val port = 8088

      val _server = ServerBuilder()
      .codec(RichHttp[Request](Http()))
      .bindTo(new InetSocketAddress(port))
      .name("Finagle Hack-a-thon")
      .build(service)

      println("Server start up! port: %d".format(port))

      _server
    }

  }
  
  def retrieve(startAfter:Long):String = {
    import java.sql._
    val array = new scala.collection.mutable.ArrayBuffer[String]()
    val con = getConnection()
    try {
      val stmt = con.prepareStatement("select id,message from events where id > ? order by id")
      stmt.setLong(1, startAfter)
      val rs = stmt.executeQuery()
      while(rs.next()){
        array.append("%s\t%s".format(rs.getLong("id"), rs.getString("message")))
      }
      stmt.close()
    } finally {
      con.close()
    }
    array.mkString("\n")
  }

  def publish(msg:String):Unit = {
    import java.sql._
    val con = getConnection()
    try {
      val stmt = con.prepareStatement("insert into events(message,created_at) values(?,?)")
      stmt.setString(1, msg)
      stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis))
      stmt.executeUpdate()
      stmt.close()
    } finally {
      con.close()
    }
  }

  /**
   * データベース接続の参照
   */
  private def getConnection():java.sql.Connection = {
    import java.sql._

    // TODO コネクションプール化しる
    Class.forName("com.mysql.jdbc.Driver")
    val con = DriverManager.getConnection("jdbc:mysql://localhost/whiteboard", "root", "root")
    try{
      con.setAutoCommit(true)
      con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)
      con
    } catch {
      case ex:Throwable =>
        con.close
        throw ex
    }
  }

}

/*
 $ curl localhost:8080
 Hello, world!
 $ curl localhost:8080/name/toshi
 Hello, toshi!%
*/