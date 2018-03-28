// edited: 12/03/2018

/**
 * Hierarchy
 *
 * MainSupervisor
 * -> Supervisor
 * ->-> Child
 * ->->-> Abacus
 *
 */

// import libraries
import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global

//////////////////////////////////////////////////////////////////////
//////////////////////// define case classes /////////////////////////
//////////////////////////////////////////////////////////////////////
case class StartTest()
case class CommenceOperations()
case class Shutdown()
case class StopNow()
case class MyFirstAbacus(first: Int, second: Int)

//////////////////////////////////////////////////////////////////////
//////////// object TestingSupervision, inherits from App ////////////
//////////// serves as main and tests the Main Supervisor ////////////
//////////////////////////////////////////////////////////////////////
object TestingSupervision extends App {

  val system = ActorSystem("Sys") // actor system
  val supervisorMain = system.actorOf(Props[MainSupervisor], "supervisorMain") // top level supervisor, never dies by itself
  supervisorMain ! StartTest // begin testing
  // comment the next line out to make system stay alive forever - useful if you wish to see whether the top level supervisor stays alive even if all other actors die out
  supervisorMain ! Shutdown // terminate the system once all testing has finished

} // end of TestingSupervision object

//////////////////////////////////////////////////////////////////////
///// MainSupervisor class, inherits from Actor and ActorLogging /////
////////////////// acts as master to Supervisor //////////////////////
//////////////////////////////////////////////////////////////////////
class MainSupervisor extends Actor with ActorLogging {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._ // provides Resume/Restart/Stop/Escalate
  import scala.concurrent.duration._

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(1, "minute")) {
    case _: ArithmeticException =>
      log.info("MainSupervisor: ArithmeticException has reached me, carry on"); Resume
    case _: NullPointerException =>
      log.info("MainSupervisor: NullPointerException has reached me, restart the Supervisor"); Restart
    case _: IllegalArgumentException =>
      log.info("MainSupervisor: IllegalArgumentException has reached me, stop the Supervisor"); Stop
    case _: Exception => log.info("MainSupervisor: Exception has reached me, stop the Supervisor - Exception message will now be visible"); Stop
  } // end of MainSupervisor supervisorStrategy

  // receive method for MainSupervisor class
  def receive = {

    case StartTest =>

      val supervisor = context.actorOf(Props[Supervisor], "supervisor") // sub-supervisor
      self ! supervisor // assign the sub-supervisor to the main supervisor

      supervisor ! CommenceOperations // start the real tests, see below

    case p: Props =>
      sender ! context.actorOf(p)

    case Shutdown =>
      // add 2 seconds delay to shutdown to allow all actor messages to reach their targets
      context.system.scheduler.scheduleOnce(Duration(2, "seconds")) {
        self ! StopNow // call the below case, system shutdown
      } // end of scheduleOnce method

    case StopNow =>
      context.system.shutdown()
  } // end of receive method

  override def preStart() {
    log.info(s"MainSupervisor About to Start.")
    super.preStart() // carry out the procedure
  } // end of preStart method

  override def postRestart(reason: Throwable) {
    val reasonStr = reason.getMessage
    log.info(s"MainSupervisor Restarted. Reason: $reasonStr")
    super.postRestart(reason) // carry out the procedure
  } // end of postRestart method

  override def postStop() {
    super.postStop() // carry out the procedure
    log.info(s"MainSupervisor Stopped.")
  } // end of postStop method
} // end of MainSupervisor class

//////////////////////////////////////////////////////////////////////
// Supervisor class, inherits from Actor and Actorlogging ////////////
// acts as slave to MainSupervisor and master of Child ///////////////
//////////////////////////////////////////////////////////////////////
class Supervisor extends Actor with ActorLogging {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._ // provides Resume/Restart/Stop/Escalate
  import scala.concurrent.duration._

  // define strategy for handling issues
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(1, "minute")) {
    case _: ArithmeticException =>
      log.info("Supervisor: No issues, Resuming child"); Resume
    case _: NullPointerException =>
      log.info("Supervisor: Minor issues, Restarting child"); Restart
    case _: IllegalArgumentException =>
      log.info("Supervisor: Major issues, Stopping child forever, no Escalating"); Stop
    case _: Exception => log.info("Supervisor: Crysis! Child died - Escalating issue to MainSupervisor - exception will not be visible at this level"); Escalate
  } // end of Supervisor supervisorStrategy

  // receive method for Supervisor class
  def receive = {

    // take control of the actor
    case p: Props =>
      sender ! context.actorOf(p)

    // comment/uncomment groups (2 lines at a time, pairs) to test out the different supervision strategy cases
    case CommenceOperations =>

      // normal operation
      val child = context.actorOf(Props[Child], "child") // spawn the child within current context
      self ! child // assign the new child to the supervisor

      child ! 42 // set state to 42
      child ! "get"

    // crash and force RESUME
    //child ! new ArithmeticException // crash it
    //child ! "get" // should still say 42

    // crash and force RESTART
    //child ! new NullPointerException // crash it harder
    //child ! "get" // should say 0, the default

    // crash and force STOP
    //child ! new IllegalArgumentException // crash it more
    //child ! "get" // message undelivered, this child died - see DeadLetters warning

    // crash and force ESCALATE to MainSupervisor
    //child ! new Exception("CRASH") // and crash again
    //child ! "get" // message undelivered, this child died - see DeadLetters warning
  } // end of receive method

  override def preStart() {
    log.info(s"Supervisor About to Start.")
    super.preStart() // carry out the procedure
  } // end of preStart method

  override def postRestart(reason: Throwable) {
    val reasonStr = reason.getMessage
    log.info(s"Supervisor Restarted. Reason: $reasonStr")
    super.postRestart(reason) // carry out the procedure
  } // end of postRestart method

  override def postStop() {
    super.postStop() // carry out the procedure
    log.info(s"Supervisor Stopped. All children have been killed.")
  } // end of postStop method
} // end of Supervisor Class

//////////////////////////////////////////////////////////////////////
//////////// Child class, inherits from Actor and ActorLogging ///////
//////////// acts as slave to Supervisor, is master of Abacus ////////
//////////////////////////////////////////////////////////////////////
class Child extends Actor with ActorLogging {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._ // provides Resume/Restart/Stop/Escalate
  import scala.concurrent.duration._

  // define strategy for handling issues
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(1, "minute")) {
    case _: ArithmeticException =>
      log.info("Supervisor: No issues, Resuming abacus"); Resume
    case _: NullPointerException =>
      log.info("Supervisor: Minor issues, Restarting abacus"); Restart
    case _: IllegalArgumentException =>
      log.info("Supervisor: Major issues, Stopping abacus forever, no Escalating"); Stop
    case _: Exception => log.info("Supervisor: Crysis! Abacus died - Escalating issue to Supervisor - exception will not be visible at this level"); Escalate
  } // end of supervisorStrategy

  // state of actor 0 by default, passed to child in Supervisor case commenceOperations
  var state = 0

  // receive method for Child class
  def receive = {

    // set up props for Child class
    case p: Props =>
      sender ! context.actorOf(p) // take control of the actor

    // exception for handling problems with the abacus
    case ex: Exception =>

      val msg = ex.getMessage
      log.info(s"Exception Thrown: <$msg>.")
      throw ex // carry out the action

    // set state of this child actor
    case x: Int =>
      state = x
      log.info(s"State is now <$state>.")

    // get state of this child actor
    case "get" =>

      log.info(s"Current State: <$state>")
  } // end of receive method

  override def preStart() {
    log.info(s"Child About to Start.")

    val abacus = context.actorOf(Props[Abacus], "abacus") // spawn the child within current context
    self ! abacus // assign the new child to the supervisor

    // assign management of the abacus to the child (values passed to be summed)
    abacus ! MyFirstAbacus(9, 1)

    super.preStart() // carry out the procedure
  } // end of preStart method

  override def postRestart(reason: Throwable) {
    val reasonStr = reason.getMessage
    log.info(s"Child Restarted. Reason: $reasonStr")
    super.postRestart(reason) // carry out the procedure
  } // end of postRestart method

  override def postStop() {
    super.postStop() // carry out the procedure
    log.info(s"Child Stopped.")
  } // end of postStop method
} // end of Child class

//////////////////////////////////////////////////////////////////////
///////////////// Abacus class, inherits from Actor //////////////////
////////////////////// acts as slave to Child ////////////////////////
//////////////////////////////////////////////////////////////////////
class Abacus extends Actor {

  // receive method for Abacus class
  def receive = {

    case MyFirstAbacus(first, second) =>

      // test if either of the abacus numbers is 0,
      if (first <= 0 || second <= 0) {
        // if true throw Exception
        throw new Exception("At least one of the values is zero or less. Both numbers must be greater than 0.")
      } // end of if test

      val sum = first + second // add the values
      println(s"$first and $second are the numbers, the sum is: $sum.") // print the values
  } // end of case class
} // end of Abacus class