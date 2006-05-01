/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This implementation is based on the "cohere" and "pwelch" 
 * functions in Octave Forge.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import org.apache.log4j.Logger;

/**
## usage: [Cxy, w] = cohere(x, y, ...)
##
## Estimate coherence between two signals.
## This is simply Cxy = |Pxy|^2/(PxxPxy).
##
## See pwelch for an explanation of the available parameters.

 elseif nargout==1
    Cxy=pwelch('cohere',varargin{:});
    varargout{1} = Cxy;

/**
## Estimate power spectrum of a stationary signal. This chops the signal
## into overlapping slices, windows each slice and applies a Fourier
## transform to determine the frequency components at that slice. The
## magnitudes of these slices are then averaged to produce the estimate Pxx.
## The confidence interval around the estimate is returned in Pci.
##
## x: vector of samples
## n: size of fourier transform window, or [] for default=256
## Fs: sample rate, or [] for default=2 Hz
## window: shape of the fourier transform window, or [] for default=hanning(n)
##    Note: window length can be specified instead, in which case
##    window=hanning(length)
## overlap: overlap with previous window, or [] for default=length(window)/2
## ci: confidence interval, or [] for default=0.95
##    ci must be between 0 and 1; if ci is not passed, or if it is
##    passed as 0, then no confidence intervals will be computed.
## range: 'whole',  or [] for default='half'
##    show all frequencies, or just half of the frequencies
## units: 'squared', or [] for default='db'
##    show results as magnitude squared or as log magnitude squared
## trend: 'mean', 'linear', or [] for default='none'
##    remove trends from the data slices before computing spectral estimates

 * 
 * @author rene
 *
 */
public class Coherence {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(Coherence.class);

	public float[] cohere(float[] s1, float[] s2, int windowsize, int samplerate, int overlap
			/*float confidenceInterval*/) {
		if (s1.length != s2.length) {
			logger.error("Signals have different length");
			return null;
		}
		
		// default for windowsize
		if (windowsize <= 0)
			windowsize = s1.length >= 256 ? s1.length : 256;
		// default for samplerate
		if (samplerate <= 0)
			samplerate = 2; // Hz
		// default for overlap
		if (overlap <= 0)
			overlap = windowsize / 2;
		// default confidence interval - not needed just for the coherence
		/*if (confidenceInterval <= 0)
			confidenceInterval = 0.95f;*/
		
		// generate hanning window
		// normalize hanning window: window = window / norm(window);
		
		return null;
	}
/*
## Fill in defaults for arguments that aren't specified
if isempty(window), window = hanning(nfft); endif
if isempty(trend), trend=-1; endif
if isempty(use_dB),
  ## don't default to db for cohere, or for returned values
  use_dB = (ftype!=3 && nargout == 0);
endif

## compute window offsets
win_size = length(window);
if (win_size > nfft)
  nfft = win_size;
  warning (sprintf("%s fft size adjusted to %d", calledby, nfft));
end
step = win_size - overlap;

## Determine which correlations to compute
Pxx = Pyy = Pxy = [];
if ftype!=2, Pxx = zeros(nfft,1); endif # Not needed for csd
if ftype==3, Pyy = zeros(nfft,1); endif # Only needed for cohere
if ftype!=1, Pxy = zeros(nfft,1); endif # Not needed for psd

## Average the slices
offset = 1:step:length(x)-win_size+1;
N = length(offset);
for i=1:N
  a = x(offset(i):offset(i)+win_size-1);
  if trend>=0, a=detrend(a,trend); endif
  a = fft(postpad(a.*window, nfft));
  if !isempty(Pxx), Pxx = Pxx + a.*conj(a);  endif
  if !isempty(Pxy)
    b = y(offset(i):offset(i)+win_size-1);
    if trend>=0, b=detrend(b,trend); endif
    b = fft(postpad(b.*window, nfft));
    Pxy = Pxy + a .*conj(b);
    if !isempty(Pyy), Pyy = Pyy + b.*conj(b); endif
  endif
endfor
if (ftype <= 2)
  ## the factors of N cancel when computing cohere and tfe
  if !isempty(Pxx), Pxx = Pxx / N; endif
  if !isempty(Pxy), Pxy = Pxy / N; endif
  if !isempty(Pyy), Pyy = Pyy / N; endif
endif

## Compute confidence intervals
if ci > 0, Pci = zeros(nfft,1); endif
if (ci > 0 && N > 1)
  if ftype>2
    error([calledby, ": internal error -- shouldn't compute Pci"]);
  end

  ## c.i. = mean +/- dev
  ## dev = z_ci*std/sqrt(n)
  ## std = sqrt(sumsq(P-mean(P))/(N-1))
  ## z_ci = normal_inv( 1-(1-ci)/2 ) = normal_inv( (1+ci)/2 );
  ## normal_inv(x) = sqrt(2) * erfinv(2*x-1)
  ##    => z_ci = sqrt(2)*erfinv(2*(1+ci)/2-1) = sqrt(2)*erfinv(ci)
  for i=1:N
    a=x(offset(i):offset(i)+win_size-1);
    if trend>=0, a=detrend(a,trend); endif
    a=fft(postpad(a.*window, nfft));
    if ftype == 1 # psd
      P = a.*conj(a) - Pxx;
      Pci = Pci + P.*conj(P);
    else          # csd
      b=y(offset(i):offset(i)+win_size-1);
      if trend>=0, b=detrend(b,trend); endif
     b=fft(postpad(b.*window, nfft));
      P = a.*conj(b) - Pxy;
      Pci = Pci + P.*conj(P);
    endif
  endfor

  Pci = ( erfinv(ci) * sqrt( 2/N/(N-1) ) ) * sqrt ( Pci );
endif

switch (ftype)
  case 1, # psd
    P = Pxx / Fs;
    if ci > 0, Pci = Pci / Fs; endif
  case 2, # csd
    P = Pxy;
  case 3, # cohere
    P = Pxy.*conj(Pxy)./Pxx./Pyy;
  case 4, # tfe
    P = Pxy./Pxx;
endswitch

## compute confidence intervals
if ci > 0, Pci = [ P - Pci, P + Pci ]; endif

if use_dB
  P = 10.0*log10(P);
  if ci > 0, Pci = 10.0*log10(Pci); endif
endif

## extract the positive frequency components
if whole
  ret_n = nfft;
elseif rem(nfft,2)==1
  ret_n = (nfft+1)/2;
else
  ret_n = nfft/2 + 1;
end
P = P(1:ret_n, :);
if ci > 0, Pci = Pci(1:ret_n, :); endif
f = [0:ret_n-1]*Fs/nfft;

*/	

}
