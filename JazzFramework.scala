import drawings.Circle
import drawings.AnimatingChild
import drawings.AnimatingPanel
import drawings.Frame
import drawings.Rectangle
import drawings.SplitPanel
import drawings.RoundRectangle

import scala.collection.mutable.Map
import scala.language.implicitConversions
import scala.runtime._

import java.awt.event._
import java.awt._
import java.beans._
import javax.swing._

class JazzFramework extends KeyListener with PropertyChangeListener {
    private val frame = new drawings.Frame()
    frame.addKeyListener(this)
    frame.addPropertyChangeListener(this)

    private var shape_bindings = Map[Symbol, AnimatingChild]()
    private var animating_child_to_symbol = Map[AnimatingChild, Symbol]()
    private var environment_bindings = Map[Symbol, AnimatingPanel]()
    private var key_press_bindings   = Map[Int, Map[Shape, Shape => Unit]]()
    private var key_release_bindings = Map[Int, Map[Shape, Shape => Unit]]()
    private var interaction_bindings = Map[Shape, Map[Shape, Set[(Shape, Shape) => Unit]]]()

    private var panel_bindings = Map[Symbol, SplitPanel]()
    private var button_bindings = Map[Symbol, JButton]()
    private var label_bindings = Map[Symbol, JLabel]()

    private def isBound(s: Symbol): Boolean =
        shape_bindings.contains(s) || environment_bindings.contains(s) ||
        panel_bindings.contains(s) || button_bindings.contains(s) ||
        label_bindings.contains(s)

    private def assertNotBound(s: Symbol) {
        if (isBound(s))
            sys.error("Variable " + s + " is already bound.")
    }

    object Create {
        def environment(s: Symbol): Environment = {
            assertNotBound(s)
            val ap = new AnimatingPanel(frame)
            environment_bindings += (s -> ap)
            Environment(s)
        }

        def circle(s: Symbol): Shape = {
            assertNotBound(s)
            val c = new Circle()
            shape_bindings += (s -> c)
            animating_child_to_symbol += (c -> s)
            Shape(s)
        }

        def rectangle(s: Symbol): Shape = {
            assertNotBound(s)
            val r = new Rectangle()
            shape_bindings += (s -> r)
            animating_child_to_symbol += (r -> s)
            Shape(s)
        }
        
        def roundRectangle(s: Symbol): Shape = {
            assertNotBound(s)
            val rr = new RoundRectangle()
            shape_bindings += (s -> rr)
            animating_child_to_symbol += (rr -> s)
            Shape(s)
        }

        def hPanel(s: Symbol, n: Int): ScalaPanel = {
            assertNotBound(s)
            val p = new SplitPanel(n, true)
            panel_bindings += (s -> p)
            ScalaPanel(s)
        }

        def vPanel(s: Symbol, n: Int): ScalaPanel = {
            assertNotBound(s)
            val p = new SplitPanel(n, false)
            panel_bindings += (s -> p)
            ScalaPanel(s)
        }

        def button(s: Symbol): ScalaButton = {
            assertNotBound(s)
            val b = new JButton()
            b.setFocusable(false)
            button_bindings += (s -> b)
            ScalaButton(s)
        }

        def label(s: Symbol): ScalaLabel = {
            assertNotBound(s)
            val b = new JLabel()
            label_bindings += (s -> b)
            ScalaLabel(s)
        }
    }

    abstract class JazzElement
    case class Environment(s: Symbol) extends JazzElement {
        private def fetch(): AnimatingPanel = environment_bindings.get(s).get

        var lastAdded: Shape = null

        def addShape(s: Shape): Environment = {
            fetch().addChild(s.fetch())
            lastAdded = s
            this
        }

        def at(x: Int, y: Int): Environment = {
            if (lastAdded == null)
                sys.error("BAD")
            lastAdded.location(x, y)
            lastAdded = null
            this
        }

        def size(width: Int, height: Int): Environment = {
            fetch().setPreferredSize(new Dimension(width, height))
            this
        }

        def onKeyPress(key: Int, action: Shape => Unit, child: Shape): Environment = {
            if (!key_press_bindings.contains(key)) {
                var action_set = Map[Shape, Shape => Unit]()
                action_set += (child -> action)
                key_press_bindings += (key -> action_set)
            } else {
                key_press_bindings.get(key).get += (child -> action)
            }
            this
        }

        def onKeyRelease(key: Int, action: Shape => Unit, child: Shape): Environment = {
            if (!key_release_bindings.contains(key)) {
                var action_set = Map[Shape, Shape => Unit]()
                action_set += (child -> action)
                key_release_bindings += (key -> action_set)
            } else {
                key_release_bindings.get(key).get += (child -> action)
            }
            this
        }

        def mit(t: Article): Environment = this
        def und(t: Article): Environment = this
        override def toString(): String = "Environment " + s
    }

    case class Shape(s: Symbol) extends JazzElement {
        def fetch(): AnimatingChild = shape_bindings.get(s).get

        def location(x: Double, y: Double): Shape = {
            fetch().setLocation(x, y)
            this
        }

        def color(c: Color): Shape = {
            fetch().setColor(c)
            this
        }

        def velocity(direction: Double, speed: Double): Shape = {
            if (direction < 0 || direction > 360)
                sys.error("Invalid direction for velocity")
            fetch().setVelocity(direction, speed)
            this
        }

        def radius(radius: Double): Shape = {
            fetch() match {
                case c: Circle => c.setRadius(radius)
                case default => sys.error("Can only change the radius on a circle")
            }
            this
        }

        def size(width: Double, height: Double): Shape = {
            fetch() match {
                case r: Rectangle => r.setSize(width, height)
                case rr: RoundRectangle => rr.setSize(width, height)
                case default => sys.error("Can only set width on shapes with a width")
            }
            this
        }

        def arcSize(width: Double, height: Double): Shape = {
            fetch() match {
                case rr: RoundRectangle => rr.setArcSize(width, height)
                case default => sys.error("Can only change the arcSize on a round rectangle")
            }
            this
        }

        def active(active: Boolean): Shape = {
            fetch().setActive(active)
            this
        }

        def visible(visible: Boolean): Shape = {
            fetch().setVisible(visible)
            this
        }

        def interaction(s: Shape, func: (Shape, Shape) => Unit) = {
            if (!interaction_bindings.contains(this))
                interaction_bindings += (this -> Map[Shape, Set[(Shape, Shape) => Unit]]())

            val my_bindings = interaction_bindings.get(this).get

            if (!my_bindings.contains(s)) {
                val behaviors = Set(func)
                my_bindings += (s -> behaviors)
            } else {
                val behaviors = my_bindings.get(s).get
                my_bindings += (s -> (behaviors + func))
            }
            interaction_bindings += (this -> my_bindings)
            this
        }

        def mit(t: Article): Shape = this
        def und(t: Article): Shape = this
        override def toString(): String = fetch().toString()
    }

    object ScalaFrame {
        private var tooLateToSplit: Boolean = false
        def hsplit(n: Int) = {
            if (tooLateToSplit)
                sys.error("Already called split on frame")
            tooLateToSplit = true
            frame.setContentPane(new SplitPanel(n, true));
            this
        }

        def vsplit(n: Int) = {
            if (tooLateToSplit)
                sys.error("Already called split on frame")
            tooLateToSplit = true
            frame.setContentPane(new SplitPanel(n, false));
            this
        }

        def color(color: Color) = {
            frame.getContentPane().setBackground(color)
            this
        }

        def update(index: Int, comp: ScalaComponent) {
            if (!tooLateToSplit) {
                frame.setContentPane(new SplitPanel(1, true));
                tooLateToSplit = true
            }
            frame.getContentPane().asInstanceOf[SplitPanel].setChild(index, comp.fetch())
        }

        def update(index: Int, compSymbol: Symbol) {
            if (!tooLateToSplit) {
                frame.setContentPane(new SplitPanel(1, true));
                tooLateToSplit = true
            }
            if (!isBound(compSymbol))
                sys.error("Cannot find " + compSymbol)
            var comp: JComponent = null
            if (environment_bindings.contains(compSymbol))
                comp = environment_bindings.get(compSymbol).get
            if (panel_bindings.contains(compSymbol))
                comp = panel_bindings.get(compSymbol).get
            if (button_bindings.contains(compSymbol))
                comp = button_bindings.get(compSymbol).get
            if (label_bindings.contains(compSymbol))
                comp = label_bindings.get(compSymbol).get
            if (comp == null)
                sys.error("Cannot find " + compSymbol)

            frame.getContentPane().asInstanceOf[SplitPanel].setChild(index, comp)
        }
    }

    abstract class ScalaComponent extends JazzElement {
        def fetch(): JComponent
    }
    case class ScalaPanel(s: Symbol) extends ScalaComponent {
        override def fetch(): SplitPanel = panel_bindings.get(s).get

        def color(color: Color): ScalaPanel = {
            fetch().setBackground(color)
            this
        }

        def update(index: Int, comp: ScalaComponent) {
            fetch().setChild(index, comp.fetch())
        }

        def update(index: Int, compSymbol: Symbol) {
            if (!isBound(compSymbol))
                sys.error("Cannot find " + compSymbol)
            var comp: JComponent = null
            if (environment_bindings.contains(compSymbol))
                comp = environment_bindings.get(compSymbol).get
            if (panel_bindings.contains(compSymbol))
                comp = panel_bindings.get(compSymbol).get
            if (button_bindings.contains(compSymbol))
                comp = button_bindings.get(compSymbol).get
            if (label_bindings.contains(compSymbol))
                comp = label_bindings.get(compSymbol).get
            if (comp == null)
                sys.error("Cannot find " + compSymbol)

            fetch().setChild(index, comp)
        }

        def mit(t: Article): ScalaPanel = this
        def und(t: Article): ScalaPanel = this
        override def toString(): String = "ScalaPanel()"
    }

    case class ScalaButton(s: Symbol) extends ScalaComponent {
        override def fetch(): JButton = button_bindings.get(s).get

        def buttonText(str: String): ScalaButton = {
            fetch().setText(str)
            this
        }

        def mit(t: Article): ScalaButton = this
        def und(t: Article): ScalaButton = this
        override def toString(): String = "ScalaButton(" + fetch().getText() + ")"
    }

    case class ScalaLabel(s: Symbol) extends ScalaComponent {
        override def fetch(): JLabel = label_bindings.get(s).get

        def labelText(str: String): ScalaLabel = {
            fetch().setText(str)
            this
        }

        def mit(t: Article): ScalaLabel = this
        def und(t: Article): ScalaLabel = this
        override def toString(): String = "ScalaLabel(" + fetch().getText() + ")"
    }

    def Run() {
        frame.run()

        environment_bindings.foreach {
            case (symbol, ap) =>
                ap.startAnimation()
        }
    }

    class Article
    val a = new Article()
    val an = new Article()

    // need to add for all classes/objectsScala
    def about(s: Symbol) {
        if (shape_bindings.contains(s))
            println(Shape(s))
        else if (environment_bindings.contains(s))
            println(Environment(s))
        else
            println("Unbound variable")
    }

    /*implicit def symbol2JazzElement(s: Symbol): JazzElement = {
        println("here")
        var element: JazzElement = null
        if (environment_bindings.contains(s))
            element = Environment(s)
        else if (shape_bindings.contains(s))
            element = Shape(s)
        else if (panel_bindings.contains(s))
            element = ScalaPanel(s)
        else if (button_bindings.contains(s))
            element = ScalaButton(s)
        else if (label_bindings.contains(s))
            element = ScalaLabel(s)
        else
            sys.error("Variable " + s + " not found")
        element
    }*/

    /*implicit def symbol2Shape(s: Symbol) = Shape(s)
    implicit def symbol2Environment(s: Symbol) = Environment(s)
    implicit def symbol2Panel(s: Symbol) = ScalaPanel(s)
    implicit def symbol2Button(s: Symbol) = ScalaButton(s)
    implicit def symbol2Label(s: Symbol) = ScalaLabel(s)*/

    implicit def symbol2Shape(s: Symbol): Shape = Shape(s)
    implicit def symbol2Environment(s: Symbol): Environment = Environment(s)
    implicit def symbol2Panel(s: Symbol): ScalaPanel = ScalaPanel(s)
    implicit def symbol2Button(s: Symbol): ScalaButton = ScalaButton(s)
    implicit def symbol2Label(s: Symbol): ScalaLabel = ScalaLabel(s)

    def keyPressed(e: KeyEvent) {
        var key: Int = e.getKeyCode()
        if (key_press_bindings.contains(key)) {
            var shape_list = key_press_bindings.get(key).get
            shape_list.foreach { case(shape, func) => func(shape) }
        }
    }

    def keyReleased(e: KeyEvent) {
        var key: Int = e.getKeyCode()
        if (key_release_bindings.contains(key)) {
            var shape_list = key_release_bindings.get(key).get
            shape_list.foreach { case(shape, func) => func(shape) }
        }
    }

    def keyTyped(e: KeyEvent) { }

    def propertyChange(event: PropertyChangeEvent) {
        if (event.getPropertyName() != "Interaction")
            return
        val actorChild: AnimatingChild = event.getOldValue().asInstanceOf[AnimatingChild]
        val acteeChild: AnimatingChild = event.getNewValue().asInstanceOf[AnimatingChild]
        val actor: Shape = animating_child_to_symbol.get(actorChild).get
        val actee: Shape = animating_child_to_symbol.get(acteeChild).get

        if (!interaction_bindings.contains(actor))
            return
        val actor_bindings = interaction_bindings.get(actor).get
        
        if (!actor_bindings.contains(actee))
            return
        val funcs: Set[(Shape, Shape) => Unit] = actor_bindings.get(actee).get
        for (func <- funcs) {
            func(actor, actee)
        }
    }
}