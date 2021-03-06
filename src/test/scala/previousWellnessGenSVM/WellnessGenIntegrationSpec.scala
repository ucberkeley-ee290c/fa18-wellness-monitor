/*
package wellness

// *********************************************
// Import packages
// *********************************************
import java.io.{File, FileWriter}

import firFilter._
import iirFilter._
import fft._
import features._
import pca._
import svm._
import chisel3._
import chisel3.core.FixedPoint
import dsptools.numbers._

import org.scalatest.{FlatSpec, Matchers}
import wellness.FixedPointWellnessGenParams.compareBlocks
import wellness.SIntWellnessGenParams.{datapathsArr, trimTree}

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

// *********************************************
// Abstract class declarations for golden params
// *********************************************
abstract class firGenParamsTemplate {
  val taps: Seq[Double]
}

abstract class iirGenParamsTemplate {
  val tapsA: Seq[Double]
  val tapsB: Seq[Double]
}

abstract class lineLengthGenParamsTemplate {
  val windowSize: Int
}

abstract class fftBufferGenParamsTemplate {
  val lanes: Int
}

abstract class fftConfigGenTemplate {
  val nPts: Int
}

abstract class bandpowerParamsGenTemplate {
  val idxStartBin: Int
  val idxEndBin: Int
  val nBins: Int
}

abstract class pcaParamsGenTemplate {
  val nDimensions: Int
  val nFeatures: Int
}

abstract class svmParamsGenTemplate {
  val nSupports: Int
  val nFeatures: Int
  val nClasses: Int
  val nDegree: Int
  val kernelType: String
  val classifierType: String
  val codeBook: Seq[Seq[Int]]
}

abstract class configurationMemoryParamsGenTemplate {
  val nDimensions: Int
  val nFeatures: Int
  val nSupports: Int
  val nClassifiers: Int
}

// *********************************************
// Golden PCA, SVM, and Configuration Memory
// params bundle
// *********************************************
class wellnessGenIntegrationParameterBundle {
  val goldenPCAParams: pcaParamsGenTemplate = new pcaParamsGenTemplate {
    val nDimensions: Int = 0
    val nFeatures: Int = 0
  }
  val goldenSVMParams: svmParamsGenTemplate = new svmParamsGenTemplate {
    val nSupports: Int = 0
    val nFeatures:Int = 0
    val nClasses: Int = 0
    val nDegree: Int = 0
    val kernelType: String = "adel"
    val classifierType: String = "adel"
    val codeBook: Seq[Seq[Int]] = Seq.fill(1,1)(0)
  }
  val goldenConfigurationMemoryParams: configurationMemoryParamsGenTemplate = new configurationMemoryParamsGenTemplate {
    val nDimensions: Int = 0
    val nFeatures: Int = 0
    val nSupports: Int = 0
    val nClassifiers: Int = 0
  }
}


class wellnessGenIntegrationSpec extends FlatSpec with Matchers {
  behavior of "WellnessGen"

  it should "Generate and test a Wellness Monitor (FixedPoint)" in {
    val debug = 0

    val nFFT: Int = 8

    val dataWidth = 32
    val dataBP = 8
    val dataPrototype = FixedPoint(dataWidth.W, dataBP.BP)

    val wellnessGenParams1 = new wellnessGenParams[FixedPoint] {
      val dataType = dataPrototype
    }

    // Generate arr of params datapaths
    val datapathsArr: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()
    val goldenDatapathsArr: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()

    // *********************************************
    // Generate Chisel datapath from feature request
    // *********************************************
    def makeChiselBandpower(channel: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double], idxLowBin: Int, idxUpBin: Int): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new FIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val taps = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
        else if (filterType == "IIR")
          new IIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val consts_A = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
            val consts_B = filterTapsB.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
      val fftBufferParams = new FFTBufferParams[FixedPoint] {
        val protoData = dataPrototype
        val lanes = nFFT
      }
      val fftConfig = FFTConfig(
        genIn = DspComplex(dataPrototype, dataPrototype),
        genOut = DspComplex(dataPrototype, dataPrototype),
        n = nFFT,
        lanes = nFFT,
        pipelineDepth = 0,
        quadrature = false,
      )
      val bandpowerParams = new BandpowerParams[FixedPoint] {
        val idxStartBin = idxLowBin
        val idxEndBin = idxUpBin
        val nBins = nFFT
        val genIn = DspComplex(dataPrototype, dataPrototype)
        val genOut = dataPrototype
      }

      val bandpowerDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("FFTBuffer", fftBufferParams), ("FFT", fftConfig), ("Bandpower", bandpowerParams))
      bandpowerDatapath
    }

    def makeChiselLineLength(channel: Int, windowLength: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double]): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new FIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val taps = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
        else if (filterType == "IIR")
          new IIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val consts_A = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
            val consts_B = filterTapsB.map(ConvertableTo[FixedPoint].fromDouble(_))
          }

      val lineLengthParams = new lineLengthParams[FixedPoint] {
        val protoData = dataPrototype
        val windowSize = windowLength
      }

      val bufferParams = new ShiftRegParams[FixedPoint] {
        val protoData = dataPrototype
        val delay = 1
      }

      val lineLengthDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("LineLength", lineLengthParams), ("Buffer", bufferParams), ("Buffer", bufferParams))
      lineLengthDatapath
    }

    // *********************************************
    // Generate golden datapath from feature request
    // *********************************************
    def makeGoldenBandpower(channel: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double], idxLowBin: Int, idxUpBin: Int): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new firGenParamsTemplate {
            override val taps: Seq[Double] = filterTapsA
          }
        else if (filterType == "IIR")
          new iirGenParamsTemplate {
            override val tapsA: Seq[Double] = filterTapsA
            override val tapsB: Seq[Double] = filterTapsB
          }
      val fftBufferParams = new fftBufferGenParamsTemplate {
        override val lanes: Int = nFFT
      }
      val fftConfig = new fftConfigGenTemplate {
        override val nPts: Int = nFFT
      }
      val bandpowerParams = new bandpowerParamsGenTemplate {
        override val idxStartBin: Int = idxLowBin
        override val idxEndBin: Int = idxUpBin
        override val nBins: Int = nFFT
      }

      val bandpowerDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("FFTBuffer", fftBufferParams), ("FFT", fftConfig), ("Bandpower", bandpowerParams))
      bandpowerDatapath
    }

    def makeGoldenLineLength(channel: Int, windowLength: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double]): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new firGenParamsTemplate {
            override val taps: Seq[Double] = filterTapsA
          }
        else if (filterType == "IIR")
          new iirGenParamsTemplate {
            override val tapsA: Seq[Double] = filterTapsA
            override val tapsB: Seq[Double] = filterTapsB
          }

      val lineLengthParams = new lineLengthGenParamsTemplate {
        override val windowSize: Int = windowLength
      }

      val lineLengthDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("LineLength", lineLengthParams), ("Buffer", 1), ("Buffer", 1))
      lineLengthDatapath
    }

    def compareCBlocks(block1: (String, Any), block2: (String, Any)): Int = {
      if (block1._1 == block2._1)
      {
        block1._1 match
        {
          case "Bandpower" =>
            if ((block1._2.asInstanceOf[BandpowerParams[FixedPoint]].idxStartBin == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].idxStartBin) &&
              (block1._2.asInstanceOf[BandpowerParams[FixedPoint]].idxEndBin == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].idxEndBin) &&
              (block1._2.asInstanceOf[BandpowerParams[FixedPoint]].nBins == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].nBins))
            {
              return 1
            }
          case "FFTBuffer" =>
            if (block1._2.asInstanceOf[FFTBufferParams[FixedPoint]].lanes == block2._2.asInstanceOf[FFTBufferParams[FixedPoint]].lanes)
            {
              return 1
            }
          case "FFT" =>
            if ((block1._2.asInstanceOf[FFTConfig[FixedPoint]].lanes == block2._2.asInstanceOf[FFTConfig[FixedPoint]].lanes) &&
              (block1._2.asInstanceOf[FFTConfig[FixedPoint]].n == block2._2.asInstanceOf[FFTConfig[FixedPoint]].n) &&
              (block1._2.asInstanceOf[FFTConfig[FixedPoint]].pipelineDepth == block2._2.asInstanceOf[FFTConfig[FixedPoint]].pipelineDepth))
            {
              return 1
            }
          case "FIR" =>
            if (block1._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble) == block2._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble))
            {
              return 1
            }
          case "IIR" =>
            if ((block1._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_A.map(_.litToDouble) == block2._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_A.map(_.litToDouble)) &&
              (block1._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_B.map(_.litToDouble) == block2._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_B.map(_.litToDouble)))
            {
              return 1
            }
          case "LineLength" =>
            if (block1._2.asInstanceOf[lineLengthParams[FixedPoint]].windowSize == block2._2.asInstanceOf[lineLengthParams[FixedPoint]].windowSize)
            {
              return 1
            }
          case "Buffer" =>
            if (block1._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble) == block2._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble))
            {
              return 1
            }
        }
      }
      return -1
    }


    def trimCTree(arr: ArrayBuffer[Seq[(String, Any)]]): (ArrayBuffer[Seq[(String, Any)]],ArrayBuffer[Seq[(Int, Int)]]) = {
      print("starting trimTree")
      var newArray: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()

      // Generate graph of coordinates
      var coordArr: ArrayBuffer[Seq[(Int, Int)]] = ArrayBuffer()

      // Add first datapath by hand because you have to start somewhere
      // Pull out first dp sequence
      var firstDp = arr(0)
      var firstParentSeq: Seq[(Int,Int)] = Seq((0,0))
      for (i <- 1 until firstDp.length)
      {
        firstParentSeq = firstParentSeq :+ (0,i-1)
      }
      newArray += firstDp
      coordArr = coordArr :+ firstParentSeq
      print("\nAdded first datapath and edge ")

      var state = "search"
      var isDone = 0

      var compareDp_num = 0
      var compareBlock_idx = 0

      for (i <- 1 until (arr.length))
      {
        val currentDp = arr(i)
        var currentBlock = currentDp(0)
        var currentBlock_idx = 0

        var newDp: Seq[(String,Any)] = Seq()
        var newCoordDp: Seq[(Int,Int)] = Seq()
        isDone = 0

        while (isDone == 0)
        {
          state match
          {
            case "search" =>
              breakable
              { for(j <- 0 until newArray.length){
                var isBlockSame = 0
                val compareDp = newArray(j)
                val compareBlock = compareDp(0)
                isBlockSame = compareCBlocks(currentBlock,compareBlock)
                if (isBlockSame == 1)
                {
                  print("\n Found duplicate block! move to populate")
                  compareDp_num = j
                  compareBlock_idx = 1 // add one so you start at new block
                  newDp = newDp :+ ("Null",0)
                  newCoordDp = newCoordDp :+ (-9,-9)
                  state = "populate"
                  break
                }
              }}
              if (state == "search")
              {
                print("\n No duplicate block, move to finish")
                // There were no matches so currentDP is unique and can be added as a whole
                // Jump to finish
                state = "finish"
              }

            case "populate" =>
              // first find shortest dp
              var compareDp = newArray(compareDp_num)
              var loopLength = Seq(compareDp.length,currentDp.length).min
              breakable
              { for (j <- compareBlock_idx until loopLength) {
                var currentBlock = currentDp(j)
                var compareBlock = compareDp(j)
                var isBlockSame = compareCBlocks(currentBlock,compareBlock)
                if (isBlockSame == 1)
                {
                  newDp = newDp :+ ("Null",0)
                  newCoordDp = newCoordDp :+ (-9,-9)
                  print("\n" + currentBlock._1 + "is duplicate at" + (i,j) + "- keep nulling/looking")
                }
                else
                {// blocks are unique, add actual block, take appropriate coordinate and pass off to finish state
                  currentBlock_idx = j+1
                  newDp = newDp :+ currentBlock
                  newCoordDp = newCoordDp :+ coordArr(compareDp_num)(j)
                  state = "finish"
                  break
                  print("\n Found first unique block, move to finish")
                }
              }}

            case "finish" =>
              print("\n Made it to finish")
              for (m <- currentBlock_idx until currentDp.length)
              {
                print("\n populate remaining blocks, index = " + currentBlock_idx)
                newDp = newDp :+ currentDp(m)
                if (m == 0) { newCoordDp = newCoordDp :+ (newArray.length,0)}
                else { newCoordDp = newCoordDp :+ (newArray.length,m-1)}
              }
              newArray = newArray :+ newDp
              coordArr = coordArr :+ newCoordDp
              state = "search"
              isDone = 1
          }
        }
      }

      return (newArray,coordArr)
    }

    // TODO: CODE SECTION RELEVANT TO USER
    // Bandpower 1
    val filterTapsA: Seq[Double] = Seq(1.0, 2.0, 3.0, 4.0, 5.0, 0.0)
    datapathsArr += makeChiselBandpower(0, "FIR", filterTapsA, filterTapsA, 0, 2)
    goldenDatapathsArr += makeGoldenBandpower(0, "FIR", filterTapsA, filterTapsA, 0, 2)
    // Bandpower 2
    datapathsArr += makeChiselBandpower(0, "FIR", filterTapsA, filterTapsA, 0, 4)
    goldenDatapathsArr += makeGoldenBandpower(0, "FIR", filterTapsA, filterTapsA, 0, 4)
    // Line Length 1
    datapathsArr += makeChiselLineLength(0, 2, "FIR", filterTapsA, filterTapsA)
    goldenDatapathsArr += makeGoldenLineLength(0, 2, "FIR", filterTapsA, filterTapsA)

    // Rename to pass to tester
    // TODO: Change after more channels are added
    // val datapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = datapathsArr
    val goldenDatapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = goldenDatapathsArr

    val (trimmedTree,heritageArray) = trimCTree(datapathsArr)
    val datapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = trimmedTree // ADDED


    // *********************************************
    // Chisel PCA, SVM, and
    // Configuration Memory params
    // *********************************************
    val pcaParams = new PCAParams[FixedPoint] {
      val protoData = dataPrototype
      val nDimensions = 3 // input dimension, minimum 1
      val nFeatures = 2 // output dimension to SVM, minimum 1
    }

    val svmParams = new SVMParams[FixedPoint] {
      val protoData = dataPrototype
      val nSupports = 2
      val nFeatures = pcaParams.nFeatures
      val nClasses = 2
      val nDegree = 1
      val kernelType = "poly"
      val classifierType = "ovo"
      val codeBook = Seq.fill(nClasses, nClasses * 2)((scala.util.Random.nextInt(2) * 2) - 1) // ignored for this test case
    }

    val configurationMemoryParams = new ConfigurationMemoryParams[FixedPoint] {
      object computeNClassifiers {
        def apply(params: SVMParams[FixedPoint] with Object {
          val nClasses: Int
          val codeBook: Seq[Seq[Int]]
          val classifierType: String
        }): Int =
          if (params.classifierType == "ovr") {
            if (params.nClasses == 2) params.nClasses - 1
            else 1
          }
          else if (params.classifierType == "ovo") {
            (params.nClasses * (params.nClasses - 1)) / 2
          }
          else if (params.classifierType == "ecoc") {
            params.codeBook.head.length
          }
          else 1
      }
      val protoData = dataPrototype
      val nDimensions: Int = pcaParams.nDimensions
      val nFeatures: Int = pcaParams.nFeatures
      val nSupports: Int = svmParams.nSupports
      val nClassifiers: Int = computeNClassifiers(svmParams)
    }

    // *********************************************
    // Golden PCA, SVM, and
    // Configuration Memory params
    // *********************************************
    val goldenParams = new wellnessGenIntegrationParameterBundle {
      override val goldenPCAParams: pcaParamsGenTemplate = new pcaParamsGenTemplate {
        val nDimensions: Int = pcaParams.nDimensions
        val nFeatures: Int = pcaParams.nFeatures
      }
      override val goldenSVMParams: svmParamsGenTemplate = new svmParamsGenTemplate {
        val nSupports: Int = svmParams.nSupports
        val nFeatures: Int = svmParams.nFeatures
        val nClasses: Int = svmParams.nClasses
        val nDegree: Int = svmParams.nDegree
        val kernelType: String = svmParams.kernelType
        val classifierType: String = svmParams.classifierType
        val codeBook: Seq[Seq[Int]] = svmParams.codeBook
      }
      override val goldenConfigurationMemoryParams: configurationMemoryParamsGenTemplate = new configurationMemoryParamsGenTemplate {
        object computeNClassifiers {
          def apply(params: svmParamsGenTemplate with Object {
            val nClasses: Int
            val codeBook: Seq[Seq[Int]]
            val classifierType: String
          }): Int =
            if (params.classifierType == "ovr") {
              if (params.nClasses == 2) params.nClasses - 1
              else 1
            }
            else if (params.classifierType == "ovo") {
              (params.nClasses*(params.nClasses - 1))/2
            }
            else if (params.classifierType == "ecoc") {
              params.codeBook.head.length
            }
            else 1
        }
        val nDimensions: Int = goldenPCAParams.nDimensions
        val nFeatures: Int = goldenPCAParams.nFeatures
        val nSupports: Int = goldenSVMParams.nSupports
        val nClassifiers: Int = computeNClassifiers(goldenSVMParams)
      }
    }

    // Call tester
    wellnessGenIntegrationTesterFP(
      wellnessGenParams1,
      datapathParamsArr,
      heritageArray,
      pcaParams,
      svmParams,
      configurationMemoryParams,
      goldenDatapathParamsArr,
      goldenParams,
      debug,
      0
    ) should be (true)
  }

  it should "Generate and test a Wellness Monitor with Python model (FixedPoint)" in {
    val debug = 1

//    val nFFT: Int = 8

    val dataWidth = 32
    val dataBP = 8
    val dataPrototype = FixedPoint(dataWidth.W, dataBP.BP)
    // write out the dataWidth and dataBP to a file
    // these values need to be consistent between WellnessIntegrationTester, Wellness, and the C code
    val file = new FileWriter(new File("scripts/generated_files/datasize.csv"))
    file.write(f"$dataWidth,$dataBP")
    file.close()

    val Seq(windowLength, features, dimensions, supports, classes, degree) =
    /* This is the order of parameters, as written in the Python file, for reference
    fe.window,                  # windowSize, lanes, nPts, nBins
    pca.components_.shape[0],   # nFeatures
    pca.components_.shape[1],   # nDimensions
    supports.shape[0],          # nSupports
    classes,                    # nClasses
    degree                      # nDegree */
      utilities.readCSV("scripts/generated_files/parameters.csv").flatMap(_.map(_.toInt))
    val nFFT: Int = windowLength

    val wellnessGenParams1 = new wellnessGenParams[FixedPoint] {
      val dataType = dataPrototype
    }

    // Generate arr of params datapaths
    val datapathsArr: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()
    //val datapathsArr_raw: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()
    val goldenDatapathsArr: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()

    // this is my preliminary attempt to generalize the identification of bandpower indices
    // looks cool :)
    val feature_list = utilities.readCSV("scripts/generated_files/feature_list.csv").flatten

    var bandpower1Index = Seq(0, 0)
    var bandpower2Index = Seq(0, 0)
    val band_list = Seq("delta", "theta", "alpha", "beta", "gamma")

    for (i <- feature_list.indices) {
      for (j <- band_list.indices) {
        if (feature_list(i) == band_list(j)) {
          if (i == 0) {
            bandpower1Index = utilities.readCSV(f"scripts/generated_files/${band_list(j)}%s_index.csv").flatMap(_.map(_.toInt))
          } else {
            bandpower2Index = utilities.readCSV(f"scripts/generated_files/${band_list(j)}%s_index.csv").flatMap(_.map(_.toInt))
          }
        }
      }
    }

    // *********************************************
    // Generate Chisel datapath from feature request
    // *********************************************
    def makeChiselBandpower(channel: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double], idxLowBin: Int, idxUpBin: Int): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new FIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val taps = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
        else if (filterType == "IIR")
          new IIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val consts_A = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
            val consts_B = filterTapsB.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
      val fftBufferParams = new FFTBufferParams[FixedPoint] {
        val protoData = dataPrototype
        val lanes = nFFT
      }
      val fftConfig = FFTConfig(
        genIn = DspComplex(dataPrototype, dataPrototype),
        genOut = DspComplex(dataPrototype, dataPrototype),
        n = nFFT,
        lanes = nFFT,
        pipelineDepth = 0,
        quadrature = false,
      )
      val bandpowerParams = new BandpowerParams[FixedPoint] {
        val idxStartBin = idxLowBin
        val idxEndBin = idxUpBin
        val nBins = nFFT
        val genIn = DspComplex(dataPrototype, dataPrototype)
        val genOut = dataPrototype
      }

      val bandpowerDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("FFTBuffer", fftBufferParams), ("FFT", fftConfig), ("Bandpower", bandpowerParams))
      bandpowerDatapath
    }

    def makeChiselLineLength(channel: Int, windowLength: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double]): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new FIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val taps = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
          }
        else if (filterType == "IIR")
          new IIRFilterParams[FixedPoint] {
            val protoData = dataPrototype
            val consts_A = filterTapsA.map(ConvertableTo[FixedPoint].fromDouble(_))
            val consts_B = filterTapsB.map(ConvertableTo[FixedPoint].fromDouble(_))
          }

      val lineLengthParams = new lineLengthParams[FixedPoint] {
        val protoData = dataPrototype
        val windowSize = windowLength
      }

      val bufferParams = new ShiftRegParams[FixedPoint] {
        val protoData = dataPrototype
        val delay = 1
      }

      val lineLengthDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("LineLength", lineLengthParams), ("Buffer", bufferParams), ("Buffer", bufferParams))
      lineLengthDatapath
    }

    // *********************************************
    // Generate golden datapath from feature request
    // *********************************************
    def makeGoldenBandpower(channel: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double], idxLowBin: Int, idxUpBin: Int): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new firGenParamsTemplate {
            override val taps: Seq[Double] = filterTapsA
          }
        else if (filterType == "IIR")
          new iirGenParamsTemplate {
            override val tapsA: Seq[Double] = filterTapsA
            override val tapsB: Seq[Double] = filterTapsB
          }
      val fftBufferParams = new fftBufferGenParamsTemplate {
        override val lanes: Int = nFFT
      }
      val fftConfig = new fftConfigGenTemplate {
        override val nPts: Int = nFFT
      }
      val bandpowerParams = new bandpowerParamsGenTemplate {
        override val idxStartBin: Int = idxLowBin
        override val idxEndBin: Int = idxUpBin
        override val nBins: Int = nFFT
      }

      val bandpowerDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("FFTBuffer", fftBufferParams), ("FFT", fftConfig), ("Bandpower", bandpowerParams))
      bandpowerDatapath
    }

    def makeGoldenLineLength(channel: Int, windowLength: Int, filterType: String, filterTapsA: Seq[Double], filterTapsB: Seq[Double]): Seq[(String, Any)] = {
      val filterParams =
        if (filterType == "FIR")
          new firGenParamsTemplate {
            override val taps: Seq[Double] = filterTapsA
          }
        else if (filterType == "IIR")
          new iirGenParamsTemplate {
            override val tapsA: Seq[Double] = filterTapsA
            override val tapsB: Seq[Double] = filterTapsB
          }

      val lineLengthParams = new lineLengthGenParamsTemplate {
        override val windowSize: Int = windowLength
      }

      val lineLengthDatapath: Seq[(String, Any)] = Seq((filterType, filterParams), ("LineLength", lineLengthParams), ("Buffer", 1), ("Buffer", 1))
      lineLengthDatapath
    }

    // ADD RYAN'S CODE HERE
    //
    def compareCBlocks(block1: (String, Any), block2: (String, Any)): Int = {
      if (block1._1 == block2._1)
      {
        block1._1 match
        {
          case "Bandpower" =>
            if ((block1._2.asInstanceOf[BandpowerParams[FixedPoint]].idxStartBin == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].idxStartBin) &&
              (block1._2.asInstanceOf[BandpowerParams[FixedPoint]].idxEndBin == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].idxEndBin) &&
              (block1._2.asInstanceOf[BandpowerParams[FixedPoint]].nBins == block2._2.asInstanceOf[BandpowerParams[FixedPoint]].nBins))
            {
              return 1
            }
          case "FFTBuffer" =>
            if (block1._2.asInstanceOf[FFTBufferParams[FixedPoint]].lanes == block2._2.asInstanceOf[FFTBufferParams[FixedPoint]].lanes)
            {
              return 1
            }
          case "FFT" =>
            if ((block1._2.asInstanceOf[FFTConfig[FixedPoint]].lanes == block2._2.asInstanceOf[FFTConfig[FixedPoint]].lanes) &&
              (block1._2.asInstanceOf[FFTConfig[FixedPoint]].n == block2._2.asInstanceOf[FFTConfig[FixedPoint]].n) &&
              (block1._2.asInstanceOf[FFTConfig[FixedPoint]].pipelineDepth == block2._2.asInstanceOf[FFTConfig[FixedPoint]].pipelineDepth))
            {
              return 1
            }
          case "FIR" =>
            if (block1._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble) == block2._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble))
            {
              return 1
            }
          case "IIR" =>
            if ((block1._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_A.map(_.litToDouble) == block2._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_A.map(_.litToDouble)) &&
              (block1._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_B.map(_.litToDouble) == block2._2.asInstanceOf[IIRFilterParams[FixedPoint]].consts_B.map(_.litToDouble)))
            {
              return 1
            }
          case "LineLength" =>
            if (block1._2.asInstanceOf[lineLengthParams[FixedPoint]].windowSize == block2._2.asInstanceOf[lineLengthParams[FixedPoint]].windowSize)
            {
              return 1
            }
          case "Buffer" =>
            if (block1._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble) == block2._2.asInstanceOf[FIRFilterParams[FixedPoint]].taps.map(_.litToDouble))
            {
              return 1
            }
        }
      }
      return -1
    }


    def trimCTree(arr: ArrayBuffer[Seq[(String, Any)]]): (ArrayBuffer[Seq[(String, Any)]],ArrayBuffer[Seq[(Int, Int)]]) = {
      print("starting trimTree")
      var newArray: ArrayBuffer[Seq[(String, Any)]] = ArrayBuffer()

      // Generate graph of coordinates
      var coordArr: ArrayBuffer[Seq[(Int, Int)]] = ArrayBuffer()

      // Add first datapath by hand because you have to start somewhere
      // Pull out first dp sequence
      var firstDp = arr(0)
      var firstParentSeq: Seq[(Int,Int)] = Seq((0,0))
      for (i <- 1 until firstDp.length)
      {
        firstParentSeq = firstParentSeq :+ (0,i-1)
      }
      newArray += firstDp
      coordArr = coordArr :+ firstParentSeq
      print("\nAdded first datapath and edge ")

      var state = "search"
      var isDone = 0

      var compareDp_num = 0
      var compareBlock_idx = 0

      for (i <- 1 until (arr.length))
      {
        val currentDp = arr(i)
        var currentBlock = currentDp(0)
        var currentBlock_idx = 0

        var newDp: Seq[(String,Any)] = Seq()
        var newCoordDp: Seq[(Int,Int)] = Seq()
        isDone = 0

        while (isDone == 0)
        {
          state match
          {
            case "search" =>
              breakable
              { for(j <- 0 until newArray.length){
                var isBlockSame = 0
                val compareDp = newArray(j)
                val compareBlock = compareDp(0)
                isBlockSame = compareCBlocks(currentBlock,compareBlock)
                if (isBlockSame == 1)
                {
                  print("\n Found duplicate block! move to populate")
                  compareDp_num = j
                  compareBlock_idx = 1 // add one so you start at new block
                  newDp = newDp :+ ("Null",0)
                  newCoordDp = newCoordDp :+ (-9,-9)
                  state = "populate"
                  break
                }
              }}
              if (state == "search")
              {
                print("\n No duplicate block, move to finish")
                // There were no matches so currentDP is unique and can be added as a whole
                // Jump to finish
                state = "finish"
              }

            case "populate" =>
              // first find shortest dp
              var compareDp = newArray(compareDp_num)
              var loopLength = Seq(compareDp.length,currentDp.length).min
              breakable
              { for (j <- compareBlock_idx until loopLength) {
                var currentBlock = currentDp(j)
                var compareBlock = compareDp(j)
                var isBlockSame = compareCBlocks(currentBlock,compareBlock)
                if (isBlockSame == 1)
                {
                  newDp = newDp :+ ("Null",0)
                  newCoordDp = newCoordDp :+ (-9,-9)
                  print("\n Found another duplicate - keep nulling/looking")
                }
                else
                {// blocks are unique, add actual block, take appropriate coordinate and pass off to finish state
                  currentBlock_idx = j+1
                  newDp = newDp :+ currentBlock
                  newCoordDp = newCoordDp :+ coordArr(compareDp_num)(j)
                  state = "finish"
                  break
                  print("\n Found first unique block, move to finish")
                }
              }}

            case "finish" =>
              print("\n Made it to finish")
              for (m <- currentBlock_idx until currentDp.length)
              {
                print("\n populate remaining blocks, index = " + currentBlock_idx)
                newDp = newDp :+ currentDp(m)
                if (m == 0) { newCoordDp = newCoordDp :+ (newArray.length,0)}
                else { newCoordDp = newCoordDp :+ (newArray.length,m-1)}
              }
              newArray = newArray :+ newDp
              coordArr = coordArr :+ newCoordDp
              state = "search"
              isDone = 1
          }
        }
      }

      return (newArray,coordArr)
    }
    //
    // END OF RYAN'S ADDITION

    // TODO: CODE SECTION RELEVANT TO USER
    // Bandpower 1
//    val filterTapsA: Seq[Double] = Seq(1.0, 2.0, 3.0, 4.0, 5.0, 0.0)
    // get the filter taps from the file, generated in Python
    val filterTapsA = utilities.readCSV("scripts/generated_files/filter_taps.csv").flatMap(_.map(_.toDouble))
    datapathsArr += makeChiselBandpower(0, "FIR", filterTapsA, filterTapsA, bandpower1Index(0), bandpower1Index(1))
    goldenDatapathsArr += makeGoldenBandpower(0, "FIR", filterTapsA, filterTapsA, bandpower1Index(0), bandpower1Index(1))
    // Bandpower 2
    datapathsArr += makeChiselBandpower(0, "FIR", filterTapsA, filterTapsA, bandpower2Index(0), bandpower2Index(1))
    goldenDatapathsArr += makeGoldenBandpower(0, "FIR", filterTapsA, filterTapsA, bandpower2Index(0), bandpower2Index(1))
    // Line Length 1
    datapathsArr += makeChiselLineLength(0, windowLength, "FIR", filterTapsA, filterTapsA)
    goldenDatapathsArr += makeGoldenLineLength(0, windowLength, "FIR", filterTapsA, filterTapsA)

    // Rename to pass to tester
    // TODO: Change after more channels are added

    val (trimmedTree,heritageArray) = trimCTree(datapathsArr)
    //val datapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = datapathsArr
    val datapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = trimmedTree // ADDED
    val goldenDatapathParamsArr: ArrayBuffer[Seq[(String, Any)]] = goldenDatapathsArr

    // *********************************************
    // Chisel PCA, SVM, and
    // Configuration Memory params
    // *********************************************
    val pcaParams = new PCAParams[FixedPoint] {
      val protoData = dataPrototype
      val nDimensions = dimensions // input dimension, minimum 1
      val nFeatures = features // output dimension to SVM, minimum 1
    }

    val svmParams = new SVMParams[FixedPoint] {
      val protoData = dataPrototype
      val nSupports = supports
      val nFeatures = pcaParams.nFeatures
      val nClasses = classes
      val nDegree = degree
      val kernelType = "poly"
      val classifierType = "ovo"
      val codeBook = Seq.fill(nClasses, nClasses * 2)((scala.util.Random.nextInt(2) * 2) - 1) // ignored for this test case
    }

    val configurationMemoryParams = new ConfigurationMemoryParams[FixedPoint] {
      object computeNClassifiers {
        def apply(params: SVMParams[FixedPoint] with Object {
          val nClasses: Int
          val codeBook: Seq[Seq[Int]]
          val classifierType: String
        }): Int =
          if (params.classifierType == "ovr") {
            if (params.nClasses == 2) params.nClasses - 1
            else 1
          }
          else if (params.classifierType == "ovo") {
            (params.nClasses * (params.nClasses - 1)) / 2
          }
          else if (params.classifierType == "ecoc") {
            params.codeBook.head.length
          }
          else 1
      }
      val protoData = dataPrototype
      val nDimensions: Int = pcaParams.nDimensions
      val nFeatures: Int = pcaParams.nFeatures
      val nSupports: Int = svmParams.nSupports
      val nClassifiers: Int = computeNClassifiers(svmParams)
    }

    // *********************************************
    // Golden PCA, SVM, and
    // Configuration Memory params
    // *********************************************
    val goldenParams = new wellnessGenIntegrationParameterBundle {
      override val goldenPCAParams: pcaParamsGenTemplate = new pcaParamsGenTemplate {
        val nDimensions: Int = pcaParams.nDimensions
        val nFeatures: Int = pcaParams.nFeatures
      }
      override val goldenSVMParams: svmParamsGenTemplate = new svmParamsGenTemplate {
        val nSupports: Int = svmParams.nSupports
        val nFeatures: Int = svmParams.nFeatures
        val nClasses: Int = svmParams.nClasses
        val nDegree: Int = svmParams.nDegree
        val kernelType: String = svmParams.kernelType
        val classifierType: String = svmParams.classifierType
        val codeBook: Seq[Seq[Int]] = svmParams.codeBook
      }
      override val goldenConfigurationMemoryParams: configurationMemoryParamsGenTemplate = new configurationMemoryParamsGenTemplate {
        object computeNClassifiers {
          def apply(params: svmParamsGenTemplate with Object {
            val nClasses: Int
            val codeBook: Seq[Seq[Int]]
            val classifierType: String
          }): Int =
            if (params.classifierType == "ovr") {
              if (params.nClasses == 2) params.nClasses - 1
              else 1
            }
            else if (params.classifierType == "ovo") {
              (params.nClasses*(params.nClasses - 1))/2
            }
            else if (params.classifierType == "ecoc") {
              params.codeBook.head.length
            }
            else 1
        }
        val nDimensions: Int = goldenPCAParams.nDimensions
        val nFeatures: Int = goldenPCAParams.nFeatures
        val nSupports: Int = goldenSVMParams.nSupports
        val nClassifiers: Int = computeNClassifiers(goldenSVMParams)
      }
    }

    // Call tester
    wellnessGenIntegrationTesterFP(
      wellnessGenParams1,
      datapathParamsArr,
      heritageArray,
      pcaParams,
      svmParams,
      configurationMemoryParams,
      goldenDatapathParamsArr,
      goldenParams,
      debug,
      1
    ) should be (true)
  }
}
*/