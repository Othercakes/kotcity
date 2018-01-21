package kotcity.ui

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotcity.data.*
import tornadofx.App
import tornadofx.View
import tornadofx.find
import java.io.File
import javafx.stage.FileChooser
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import java.util.Optional
import javafx.scene.control.ButtonBar.ButtonData






object Algorithms {
    fun scale(valueIn: Double, baseMin: Double, baseMax: Double, limitMin: Double, limitMax: Double): Double {
        return (limitMax - limitMin) * (valueIn - baseMin) / (baseMax - baseMin) + limitMin
    }
}

const val DRAW_GRID = true
const val TICK_DELAY: Int = 10 // only render every X ticks... (framerate limiter)

enum class Tool { BULLDOZE, QUERY, ROAD, RESIDENTIAL_ZONE, INDUSTRIAL_ZONE, COMMERCIAL_ZONE, COAL_POWER_PLANT }

class GameFrame : View(), CanvasFitter {
    override val root: VBox by fxml("/GameFrame.fxml")
    private val canvas = ResizableCanvas()
    private val canvasPane: AnchorPane by fxid("canvasPane")
    private val accordion: Accordion by fxid()
    private val basicPane: TitledPane by fxid()
    private val verticalScroll: ScrollBar by fxid()
    private val horizontalScroll: ScrollBar by fxid()

    // BUTTONS
    private val roadButton: Button by fxid()
    private val queryButton: Button by fxid()
    private val bulldozeButton: Button by fxid()
    private val residentialButton: ToggleButton by fxid()
    private val commercialButton: ToggleButton by fxid()
    private val industrialButton: ToggleButton by fxid()
    private val coalPowerButton: Button by fxid()

    var ticks = 0
    private var timer: AnimationTimer? = null
    var activeTool: Tool = Tool.QUERY

    private lateinit var map: CityMap
    private var cityRenderer: CityRenderer? = null

    fun setMap(map: CityMap) {
        this.map = map
        // gotta resize the component now...
        setScrollbarSizes()
        setCanvasSize()
        initComponents()
        title = "KotCity 0.1 - ${map.cityName}"
        this.cityRenderer = CityRenderer(this, canvas, map)
    }

    private fun setCanvasSize() {
        println("map size is: ${this.map.width},${this.map.height}")
        println("Canvas pane size is: ${canvasPane.width},${canvasPane.height}")
        canvas.prefHeight(canvasPane.height - 20)
        canvas.prefWidth(canvasPane.width - 20)
        AnchorPane.setTopAnchor(canvas, 0.0)
        AnchorPane.setBottomAnchor(canvas, 20.0)
        AnchorPane.setLeftAnchor(canvas, 0.0)
        AnchorPane.setRightAnchor(canvas, 20.0)
    }

    private fun setScrollbarSizes() {

        // TODO: don't let us scroll off the edge...
        horizontalScroll.min = 0.0
        verticalScroll.min = 0.0

        horizontalScroll.max = getMap().width.toDouble()
        verticalScroll.max = getMap().height.toDouble()

    }

    private fun getMap(): CityMap {
        return this.map
    }


    fun zoomOut() {
        cityRenderer?.let {
            it.zoom -= 1
            if (it.zoom < 1) {
                it.zoom = 1.0
            }
        }
    }

    fun zoomIn() {
        cityRenderer?.let {
            it.zoom += 1
            if (it.zoom > 5.0) {
                it.zoom = 5.0
            }
        }
    }

    fun saveCity() {
        // make sure we have a filename to save to... otherwise just bounce to saveCityAs()...
        if (map.fileName != null) {
            CityFileAdapter.save(map, File(map.fileName))
            saveMessageBox()
        } else {
            saveCityAs()
        }
    }

    fun newCityPressed() {
        println("New city was pressed!")
        this.currentStage?.close()
        val launchScreen = tornadofx.find(LaunchScreen::class)
        timer?.stop()
        launchScreen.openWindow()
    }

    fun saveCityAs() {
        val fileChooser = FileChooser()
        fileChooser.title = "Save your city"
        fileChooser.extensionFilters.addAll(
            FileChooser.ExtensionFilter("KotCity City", "*.kcity")
        )
        fileChooser.initialFileName = "*.kcity"
        val file = fileChooser.showSaveDialog(this.primaryStage)
        CityFileAdapter.save(map, file)
        map.fileName = file.absoluteFile.toString()
        println("The new map filename is: ${map.fileName}")
        saveMessageBox()
    }

    private fun saveMessageBox() {
        val alert = Alert(AlertType.INFORMATION)
        alert.title = "City Saved"
        alert.headerText = "City Saved OK!"
        alert.dialogPane.content = Label("Everything went great. Your city is saved to ${map.fileName}.")
        alert.showAndWait()
    }

    fun bindButtons() {
        roadButton.setOnAction { activeTool = Tool.ROAD }
        queryButton.setOnAction { activeTool = Tool.QUERY }
        bulldozeButton.setOnAction { activeTool = Tool.BULLDOZE }
        residentialButton.setOnAction { activeTool = Tool.RESIDENTIAL_ZONE }
        commercialButton.setOnAction { activeTool = Tool.COMMERCIAL_ZONE }
        industrialButton.setOnAction { activeTool = Tool.INDUSTRIAL_ZONE }
        coalPowerButton.setOnAction { activeTool = Tool.COAL_POWER_PLANT }
    }

    fun loadCityPressed() {
        this.currentStage?.close()
        CityLoader.loadCity(this.primaryStage)
        title = "KotCity 0.1 - ${map.cityName}"
    }

    init {
        initComponents()
    }

    private fun initComponents() {
        title = "Kotcity 0.1"

        bindCanvas()
        bindButtons()

        accordion.expandedPane = basicPane

        timer = object : AnimationTimer() {
            override fun handle(now: Long) {
                if (ticks == TICK_DELAY) {
                    cityRenderer?.render()
                    ticks = 0
                }
                ticks++
            }
        }
        timer?.start()
    }

    fun quitPressed() {
        val alert = Alert(AlertType.CONFIRMATION)
        alert.title = "Quitting KotCity"
        alert.headerText = "Are you ready to leave?"
        alert.contentText = "Please confirm..."

        val buttonTypeOne = ButtonType("Yes, please quit.")
        val buttonTypeTwo = ButtonType("No, I want to keep playing.")
        val buttonTypeCancel = ButtonType("Cancel", ButtonData.CANCEL_CLOSE)

        alert.buttonTypes.setAll(buttonTypeOne, buttonTypeTwo, buttonTypeCancel)

        val result = alert.showAndWait()
        when {
            result.get() == buttonTypeOne -> System.exit(1)
            result.get() == buttonTypeTwo -> {
                // don't do anything...
            }
            else -> {
                // don't do anything..
            }
        }
    }

    private fun bindCanvas() {
        // TODO: we are handling scrolling ourself... so we have to figure out what's
        //       visible and what's not...
        canvas.prefHeight(canvasPane.height - 20)
        canvas.prefWidth(canvasPane.width - 20)
        canvasPane.add(canvas)

        canvasPane.widthProperty().addListener { _, _, newValue ->
            println("resizing canvas width to: $newValue")
            canvas.width = newValue.toDouble()
            setCanvasSize()
            setScrollbarSizes()
        }

        canvas.setOnMouseMoved { evt ->
            cityRenderer?.onMouseMoved(evt)
        }

        canvas.setOnMousePressed { evt ->
            cityRenderer?.onMousePressed(evt)
        }

        canvas.setOnMouseReleased { evt ->
            cityRenderer?.let {
                it.onMouseReleased(evt)
                val (firstBlock, lastBlock) = it.blockRange()
                if (firstBlock != null && lastBlock != null) {
                    if (evt.button == MouseButton.PRIMARY) {
                        if (activeTool == Tool.ROAD) {
                            println("Want to build road from: $firstBlock, $lastBlock")
                            map.buildRoad(firstBlock, lastBlock)
                        }  else if (activeTool == Tool.BULLDOZE) {
                            map.bulldoze(firstBlock, lastBlock)
                        }
                    }
                }
            }

        }

        canvas.setOnMouseDragged { evt ->
            cityRenderer?.onMouseDragged(evt)
        }

        canvas.setOnMouseClicked { evt ->
            cityRenderer?.onMouseClicked(evt)
            // now let's handle some tools...
            if (evt.button == MouseButton.PRIMARY) {
                if (activeTool == Tool.COAL_POWER_PLANT) {
                    // TODO: we have to figure out some kind of offset for this shit...
                    // can't take place at hovered block...
                    cityRenderer?.getHoveredBlock()?.let {
                        val newX = it.x - 1
                        val newY = it.y - 1
                        map.build(CoalPowerPlant(), BlockCoordinate(newX, newY))
                    }
                }
            }

        }

        canvasPane.heightProperty().addListener { _, _, newValue ->
            println("resizing canvas height to: $newValue")
            canvas.height = newValue.toDouble()
            setCanvasSize()
            setScrollbarSizes()
        }

        horizontalScroll.valueProperty().addListener { _, _, newValue ->
            cityRenderer?.blockOffsetX = newValue.toDouble()
        }

        verticalScroll.valueProperty().addListener { _, _, newValue ->
            cityRenderer?.blockOffsetY = newValue.toDouble()
        }

        with(canvas) {
            this.setOnScroll { scrollEvent ->
                // println("We are scrolling: $scrollEvent")
                if (scrollEvent.deltaY < 0) {
                    println("Zoom out!")
                    zoomOut()
                } else if (scrollEvent.deltaY > 0) {
                    println("Zoom in!")
                    zoomIn()
                }
            }
        }
    }

}

class GameFrameApp : App(GameFrame::class, KotcityStyles::class) {
    override fun start(stage: Stage) {
        stage.isResizable = true
        val gameFrame = find(GameFrame::class)
        val mapGenerator = MapGenerator()
        val map = mapGenerator.generateMap(512, 512)
        gameFrame.setMap(map)
        stage.isMaximized = true
        super.start(stage)
    }
}

fun main(args: Array<String>) {
    Application.launch(GameFrameApp::class.java, *args)
}