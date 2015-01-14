package org.jetbrains.plugins.scala.project.settings

import com.intellij.openapi.components.StoragePathMacros._
import com.intellij.openapi.components._
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.{SkipDefaultValuesSerializationFilters, XmlSerializer}
import org.jdom.Element

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
@State(name = "ScalaCompilerConfiguration", storages = Array (
  new Storage(file = PROJECT_FILE),
  new Storage(file = PROJECT_CONFIG_DIR + "/scala_compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)))
class ScalaCompilerConfiguration(project: Project) extends PersistentStateComponent[Element] {
  var defaultProfile: ScalaCompilerSettingsProfile = _

  var customProfiles: Seq[ScalaCompilerSettingsProfile] = _

  clear()

  def clear() {
    customProfiles = Seq.empty
    defaultProfile = new ScalaCompilerSettingsProfile("Default")
  }

  def getSettingsForModule(module: Module): ScalaCompilerSettings = {
    val profile = customProfiles.find(_.getModuleNames.contains(module.getName)).getOrElse(defaultProfile)
    profile.getSettings
  }

  def configureSettingsForModule(module: Module, source: String, options: Seq[String]) {
    val settings = new ScalaCompilerSettings()
    settings.initFrom(options)

    if (defaultProfile.getSettings.getState == settings.getState) {
      defaultProfile.addModuleName(module.getName)
    } else if (defaultProfile.getModuleNames.isEmpty) {
      defaultProfile.setSettings(settings)
      defaultProfile.addModuleName(module.getName)
    } else {
      customProfiles.find(_.getSettings.getState == settings.getState) match {
        case Some(profile) => profile.addModuleName(module.getName)
        case None =>
          val profile = new ScalaCompilerSettingsProfile(source + " " + (customProfiles.length + 1))
          profile.setSettings(settings)
          profile.addModuleName(module.getName)
          customProfiles :+= profile
      }
    }
  }

  def getState: Element = {
    val configurationElement = XmlSerializer.serialize(defaultProfile.getSettings.getState, new SkipDefaultValuesSerializationFilters())

    customProfiles.foreach { profile =>
      val profileElement = XmlSerializer.serialize(profile.getSettings.getState, new SkipDefaultValuesSerializationFilters())
      profileElement.setName("profile")
      profileElement.setAttribute("name", profile.getName)
      profileElement.setAttribute("modules", profile.getModuleNames.asScala.mkString(","))

      configurationElement.addContent(profileElement)
    }

    configurationElement
  }

  def loadState(configurationElement: Element) {
    defaultProfile.setSettings(new ScalaCompilerSettings(XmlSerializer.deserialize(configurationElement, classOf[ScalaCompilerSettingsState])))

    customProfiles = configurationElement.getChildren("profile").asScala.map { profileElement =>
      val profile = new ScalaCompilerSettingsProfile(profileElement.getAttributeValue("name"))

      val settings = new ScalaCompilerSettings(XmlSerializer.deserialize(profileElement, classOf[ScalaCompilerSettingsState]))
      profile.setSettings(settings)

      val moduleNames = profileElement.getAttributeValue("modules").split(",").filter(!_.isEmpty)
      moduleNames.foreach(profile.addModuleName)

      profile
    }
  }
}

object ScalaCompilerConfiguration {
  def instanceIn(project: Project): ScalaCompilerConfiguration =
    ServiceManager.getService(project, classOf[ScalaCompilerConfiguration])
}