#!/usr/bin/env python3
"""Real ES3 pixel comparison: chunking must preserve photons, mask and paths."""
import tempfile
from pathlib import Path
import numpy as np
from PIL import Image
from render_gpu_optics import render

with tempfile.TemporaryDirectory() as directory:
    for analytic in (False,True):
        a=Path(directory)/'whole.png';b=Path(directory)/'chunked.png'
        render(a,width=48,height=72,samples=2,analytic=analytic,path_rows=10000)
        render(b,width=48,height=72,samples=2,analytic=analytic,
               photon_batch=997,mask_rows=3,path_rows=2)
        assert np.array_equal(np.array(Image.open(a)),np.array(Image.open(b))),analytic
        print('PASS bit-identical checker/white output: photon/mask/path chunks, analytic=',analytic)
