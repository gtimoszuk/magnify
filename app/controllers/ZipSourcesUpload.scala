package controllers

import akka.dispatch._
import java.io.File
import java.lang.String
import java.util.concurrent.Executors
import magnify.features.Sources
import magnify.model.{Json, Zip}
import magnify.modules.inject
import play.api.libs.Files
import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.Logger

/**
 * @author Cezary Bartoszuk (cezary@codilime.com)
 */
object ZipSourcesUpload extends ZipSourcesUpload(inject[Sources])

sealed class ZipSourcesUpload (protected override val sources: Sources)
    extends Controller with ProjectList {

  private val dir = Play.getFile("app/resources")

  val logger = Logger(classOf[ZipSourcesUpload].getSimpleName)

  for (file <- Play.getFile("app/resources").listFiles()) {
    logger.error("processing: " + file.getName())
    //val split = file.getName().split("\.")
    //logger.error(split(0))
    sources.add(file.getName(), new Json(file))
  }

  private type MultipartRequest = Request[MultipartFormData[Files.TemporaryFile]]

  private val allowedFormats = Set("application/zip", "application/x-java-archive",
    "application/json", "application/x-javascript", "text/javascript",
    "text/x-javascript", "text/x-json", "application/octet-stream")

  private val progress = "success" -> "Project uploaded. Interpreting in background."

  implicit val pool: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  def form = Action { implicit request =>
    Ok(views.html.newProject())
  }

  def upload = Action(parse.multipartFormData) { implicit request =>
    for (file <- projectSources(request)) Future {
      for (name <- projectName) sources.add(name, new Zip(file))
    }
    Redirect(routes.ZipSourcesUpload.form()).flashing(progress)
  }

  def uploadJson = Action(parse.multipartFormData) { implicit request =>
    for (file <- projectSources(request)) Future {
      for (name <- projectName) {
        sources.add(name, new Json(file))
      }
    }
    Redirect(routes.ZipSourcesUpload.form()).flashing(progress)
  }

  private def projectName(implicit request: MultipartRequest): Option[String] =
    request.body.dataParts.get("project-name").flatMap {
      case Seq(onlyName) => Some(onlyName)
      case _ => None
    }

  private def projectSources(request: MultipartRequest): Option[File] =
    for {
      filePart <- request.body.file("project-sources")
      if filePart.contentType.map(allowedFormats) getOrElse false
    } yield {
      val newFile = File.createTempFile(filePart.filename, ".tmp.zip")
      filePart.ref.moveTo(newFile, replace=true)
      newFile
    }
}
