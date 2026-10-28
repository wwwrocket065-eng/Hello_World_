"""Numerical checks independent of the Android implementation."""
import unittest
import numpy as np
from glass_optical_reference import HollowLathe, ReferenceScene, dielectric, unit


class OpticsTest(unittest.TestCase):
    def test_normal_incidence_fresnel(self):
        _, t, f, tir = dielectric(np.array([[0., -1., 0.]]), np.array([[0., 1., 0.]]))
        np.testing.assert_allclose(t, [[0, -1, 0]])
        np.testing.assert_allclose(f, [((1.52-1)/(1.52+1))**2])
        self.assertFalse(tir[0])

    def test_snell_and_reciprocity(self):
        d = unit(np.array([[.5, -1., 0.]]))
        n = np.array([[0., 1., 0.]])
        _, t, f, _ = dielectric(d, n)
        np.testing.assert_allclose(t[:, 0]*1.52, d[:, 0])
        _, back, reverse_f, _ = dielectric(-t, n)
        np.testing.assert_allclose(back, -d)
        np.testing.assert_allclose(f, reverse_f)

    def test_total_internal_reflection(self):
        a = np.deg2rad(50)
        _, _, f, tir = dielectric(np.array([[np.sin(a), np.cos(a), 0.]]), np.array([[0., 1., 0.]]))
        self.assertTrue(tir[0])
        self.assertEqual(f[0], 1.)

    def test_inner_outer_wall_and_axis_closure(self):
        shape = HollowLathe([[0, 1], [2, 1], [2, .8], [.2, .8]], smooth=False)
        t, n = shape.intersect(np.array([[2., 1., 0.], [0., 1., 0.], [0., 3., 0.]]),
                               np.array([[-1., 0., 0.], [1., 0., 0.], [0., -1., 0.]]))
        np.testing.assert_allclose(t, [1., .8, 2.8])
        np.testing.assert_allclose(n, [[1, 0, 0], [-1, 0, 0], [0, 1, 0]])

    def test_photon_energy_budget_and_light_direction(self):
        profile = [[0, 1], [2, 1], [2, .8], [.2, .8]]
        a = ReferenceScene(profile, sun=(.6, .8, 0))
        a.glass = HollowLathe(profile, smooth=False)
        flux = a.photons(size=48, samples=2)
        budget = a.stats
        self.assertAlmostEqual(sum(budget[k] for k in ('deposited', 'escaped', 'outside', 'discarded')),
                               budget['launched'], places=6)
        self.assertTrue(np.isfinite(flux).all())
        self.assertGreater(float(flux.max()), 1.1)
        self.assertLess(float(flux.min()), .9)
        b = ReferenceScene(profile, sun=(-.6, .8, 0))
        b.glass = a.glass
        reverse = b.photons(size=48, samples=2)
        # Direct flux pattern must respond to solar azimuth, not fixed rings.
        self.assertGreater(float(np.mean(abs(flux-reverse))), .04)


if __name__ == '__main__':
    unittest.main()
