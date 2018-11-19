package fft

import wellness._
import chisel3._
import chisel3.core.FixedPoint
import dsptools._
import freechips.rocketchip.config.Parameters

import scala.util.Random
import breeze.math.Complex
import breeze.signal.fourierTr
import breeze.linalg.DenseVector

class CustomFFTTester[T <: Data](c: FFT[T], config: FFTConfig[FixedPoint]) extends DspTester(c) {
  val tone = (0 until config.n).map(x => Complex(Random.nextDouble(), 0.0))
  val expected = fourierTr(DenseVector(tone.toArray)).toArray

  tone.zip(c.io.in.bits).foreach { case(sig, port) => poke(port, sig) }
  poke(c.io.in.valid, value = 1)
  step(1)
  for (i <- 0 until c.io.out.bits.size) {
    fixTolLSBs.withValue(19) {
      expect(c.io.out.bits(i), expected(i), msg = s"Input: $tone, Expected: ${expected(i)}, ${peek(c.io.out.bits(i))}")
    }
  }
}
object FixedPointFFTTester{
  def apply(config: FFTConfig[FixedPoint]): Boolean = {
    implicit val p: Parameters = null
    dsptools.Driver.execute(() => new FFT(config), TestSetup.dspTesterOptions) {
      c => new CustomFFTTester(c, config)
    }
  }
}