package fr.adapter.pulsar

import cr.pulsar.{Subscription, Topic}
import cr.pulsar.Topic.Type

sealed trait AppTopic {
  def name: String
  def `type`: Type
  def make: Topic.Single
  def mode: Subscription.Mode = `type` match {
    case Type.Persistent    => Subscription.Mode.Durable
    case Type.NonPersistent => Subscription.Mode.NonDurable
  }
}

object AppTopic {
  private val FRTenant = "fr"

  private def forSticky(name: String, typ: Type): Topic.Single =
    Topic.single(FRTenant, "sticky", name, typ)

  private def forHandler(name: String, typ: Type): Topic.Single =
    Topic.single(FRTenant, "handler", name, typ)

  case object UserAction extends AppTopic {
    val name: String       = s"user-action"
    val `type`: Type       = Type.NonPersistent
    def make: Topic.Single = forHandler(name, `type`)
  }

  case class UserEvent(uid: String) extends AppTopic {
    val name: String       = s"user-event-$uid"
    val `type`: Type       = Type.NonPersistent
    def make: Topic.Single = forSticky(name, `type`)
  }

  case object UserTableAction extends AppTopic {
    val name: String       = s"user-table-action"
    val `type`: Type       = Type.NonPersistent
    def make: Topic.Single = forHandler(name, `type`)
  }

  case object TableEvent extends AppTopic {
    val name: String       = s"table-event"
    val `type`: Type       = Type.NonPersistent
    def make: Topic.Single = forHandler(name, `type`)
  }
}
