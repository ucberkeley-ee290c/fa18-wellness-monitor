package iirFilter

import chisel3._
import chisel3.core.FixedPoint
import dspblocks.ShiftRegisterWithReset
import dspjunctions.ValidWithSync
import dsptools.numbers._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

class IIRFilterSpec extends FlatSpec with Matchers {
  behavior of "ConstantCoefficientIIRFilter"

  it should "UInt banana" in {

    for(i <- 0 until 4) {
      val tap_count = scala.util.Random.nextInt(8) + 1

      val coefficientsA = mutable.ArrayBuffer[Double]()
      val coefficientsB = mutable.ArrayBuffer[Double]()

      for(j <- 0 until tap_count) coefficientsA += scala.util.Random.nextInt(16)
      for(j <- 0 until (tap_count+1)) coefficientsB += scala.util.Random.nextInt(16)
      val params = new IIRFilterParams[UInt] {
        val protoData = UInt(32.W)
        val consts_A = coefficientsA.map(ConvertableTo[UInt].fromDouble(_))
        val consts_B = coefficientsB.map(ConvertableTo[UInt].fromDouble(_))
      }

      UIntIIRFilterTester(params, coefficientsA, coefficientsB) should be (true)
    }
  }

  it should "SInt tomato" in {

    for(i <- 0 until 4) {
      val tap_count = scala.util.Random.nextInt(8) + 1

      val coefficientsA = mutable.ArrayBuffer[Double]()
      val coefficientsB = mutable.ArrayBuffer[Double]()

      for(j <- 0 until tap_count) coefficientsA += scala.util.Random.nextInt(16)
      for(j <- 0 until (tap_count+1)) coefficientsB += scala.util.Random.nextInt(16)
      val params = new IIRFilterParams[SInt] {
        val protoData = SInt(32.W)
        val consts_A = coefficientsA.map(ConvertableTo[SInt].fromDouble(_))
        val consts_B = coefficientsB.map(ConvertableTo[SInt].fromDouble(_))

      }
      SIntIIRFilterTester(params, coefficientsA, coefficientsB) should be (true)
    }
  }

  it should "FixedPoint strawberry" in {

    for(i <- 0 until 4) {
      val tap_count = scala.util.Random.nextInt(8) + 1
      val dataWidth = 64
      val dataBP = 32

      val coefficientsA = mutable.ArrayBuffer[Double]()
      val coefficientsB = mutable.ArrayBuffer[Double]()

      for(j <- 0 until tap_count) coefficientsA += (scala.util.Random.nextFloat * 4)
      for(j <- 0 until (tap_count+1)) coefficientsB += (scala.util.Random.nextFloat * 4)

      val params = new IIRFilterParams[FixedPoint] {
        val protoData = FixedPoint(dataWidth.W,dataBP.BP)
        val consts_A = coefficientsA.map(ConvertableTo[FixedPoint].fromDouble(_))
        val consts_B = coefficientsA.map(ConvertableTo[FixedPoint].fromDouble(_))
      }

      FixedPointIIRFilterTester(params, coefficientsA, coefficientsB) should be (true)
    }
  }

}
