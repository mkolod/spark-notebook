package notebook

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor._

import play.api.libs.iteratee._
import play.api.libs.json._

import notebook.server._
import notebook.client._

object WebSocketKernelActor {
  def props(channel: Concurrent.Channel[JsValue], calcService:CalcWebSocketService)(implicit system:ActorSystem):ActorRef =
    system.actorOf(Props(new WebSocketKernelActor(channel, calcService)))
}

class WebSocketKernelActor(channel: Concurrent.Channel[JsValue], val calcService:CalcWebSocketService)(implicit system:ActorSystem) extends Actor {
  val executionCounter = new AtomicInteger(0)

  val ws = new WebSockWrapperImpl(channel)
  calcService.wsPromise.success(ws)

  def receive = {
    case json:JsValue =>
      val header   = json \ "header"
      val session  = header \ "session"
      val msgType  = header \ "msg_type"
      val content  = json \ "content"
      val channel  = json \ "channel"

      msgType match {
        case JsString("kernel_info_request") => {
          ws.send(header, session, "info", "shell",
                  Json.obj(
                      "language_info" -> Json.obj(
                        "name"            → "scala",
                        "file_extension"  → "scala",
                        "codemirror_mode" → "text/x-scala"
                      ),
                      "extension" → "scala"
                    )
                  )
        }

        case JsString("execute_request") => {
          val JsString(code) = content \ "code"
          val execCounter = executionCounter.incrementAndGet()
          calcService.calcActor ! SessionRequest(header, session, ExecuteRequest(execCounter, code))
        }

        case JsString("complete_request") => {
          val JsString(line) = content \ "code";
          val JsNumber(cursorPos) = content \ "cursor_pos"
          calcService.calcActor ! SessionRequest(header, session, CompletionRequest(line, cursorPos.toInt))
        }

        case JsString("object_info_request") => {
          val JsString(oname) = content \ "oname"
          calcService.calcActor ! SessionRequest(header, session, ObjectInfoRequest(oname))
        }

        case x => //logWarn("Unrecognized websocket message: " + msg) //throw new IllegalArgumentException("Unrecognized message type " + x)
      }
  }

}