package com.hbm.ntm.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Feeds Blender-exported weapon JSON into the runtime animation machinery. */
final class LegacyWeaponAnimation {
    private final Map<String, Map<String, Sequence>> clips;

    private LegacyWeaponAnimation(Map<String, Map<String, Sequence>> clips) {
        this.clips = clips;
    }

    static LegacyWeaponAnimation load(ResourceManager resources, ResourceLocation file) {
        try (Reader reader = resources.openAsReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, double[]> offsets = triples(json.getAsJsonObject("offset"));
            Map<String, int[]> rotationModes = new HashMap<>();
            if (json.has("rotmode")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("rotmode").entrySet()) {
                    String mode = entry.getValue().getAsString();
                    rotationModes.put(entry.getKey(), new int[] {
                            rotation(mode.charAt(2)), rotation(mode.charAt(0)), rotation(mode.charAt(1))
                    });
                }
            }

            Map<String, Map<String, Sequence>> clips = new HashMap<>();
            for (Map.Entry<String, JsonElement> clip : json.getAsJsonObject("anim").entrySet()) {
                Map<String, Sequence> buses = new HashMap<>();
                for (Map.Entry<String, JsonElement> bus : clip.getValue().getAsJsonObject().entrySet()) {
                    buses.put(bus.getKey(), parseSequence(bus.getValue().getAsJsonObject(),
                            offsets.getOrDefault(bus.getKey(), new double[3]),
                            rotationModes.getOrDefault(bus.getKey(), new int[] {0, 1, 2})));
                }
                clips.put(clip.getKey(), Map.copyOf(buses));
            }
            return new LegacyWeaponAnimation(Map.copyOf(clips));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load legacy weapon animation " + file, exception);
        }
    }

    Transform transform(String clip, String bus, double millis) {
        Map<String, Sequence> buses = clips.get(clip);
        if (buses == null) return Transform.IDENTITY;
        Sequence sequence = buses.get(bus);
        return sequence == null ? Transform.IDENTITY : sequence.transform(millis);
    }

    static void apply(PoseStack poses, Transform transform) {
        poses.translate(transform.values[0], transform.values[1], transform.values[2]);
        for (int axis : transform.rotationOrder) {
            float angle = (float) transform.values[3 + axis];
            if (axis == 0) poses.mulPose(Axis.XP.rotationDegrees(angle));
            else if (axis == 1) poses.mulPose(Axis.YP.rotationDegrees(angle));
            else poses.mulPose(Axis.ZP.rotationDegrees(angle));
        }
        poses.translate(-transform.offset[0], -transform.offset[1], -transform.offset[2]);
        poses.scale((float) transform.values[6], (float) transform.values[7], (float) transform.values[8]);
    }

    private static Map<String, double[]> triples(JsonObject object) {
        Map<String, double[]> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonArray array = entry.getValue().getAsJsonArray();
            result.put(entry.getKey(), new double[] {
                    array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()
            });
        }
        return result;
    }

    private static int rotation(char axis) {
        return switch (axis) {
            case 'Y' -> 1;
            case 'Z' -> 2;
            default -> 0;
        };
    }

    private static Sequence parseSequence(JsonObject json, double[] offset, int[] rotationOrder) {
        @SuppressWarnings("unchecked")
        List<Keyframe>[] dimensions = (List<Keyframe>[]) new List<?>[9];
        for (int index = 0; index < dimensions.length; index++) dimensions[index] = new ArrayList<>();
        if (json.has("location")) parseChannels(json.getAsJsonObject("location"), dimensions, 0);
        if (json.has("rotation_euler")) parseChannels(json.getAsJsonObject("rotation_euler"), dimensions, 3);
        if (json.has("scale")) parseChannels(json.getAsJsonObject("scale"), dimensions, 6);
        return new Sequence(dimensions, offset.clone(), rotationOrder.clone());
    }

    private static void parseChannels(JsonObject channels, List<Keyframe>[] dimensions, int start) {
        if (channels.has("x")) parseCurve(channels.getAsJsonArray("x"), dimensions[start]);
        if (channels.has("y")) parseCurve(channels.getAsJsonArray("y"), dimensions[start + 1]);
        if (channels.has("z")) parseCurve(channels.getAsJsonArray("z"), dimensions[start + 2]);
    }

    private static void parseCurve(JsonArray array, List<Keyframe> destination) {
        Interpolation previous = null;
        for (JsonElement element : array) {
            Keyframe keyframe = parseKeyframe(element.getAsJsonArray(), previous);
            destination.add(keyframe);
            previous = keyframe.interpolation;
        }
    }

    private static Keyframe parseKeyframe(JsonArray array, Interpolation previous) {
        Keyframe frame = new Keyframe(array.get(0).getAsDouble(), array.get(1).getAsInt(),
                array.size() >= 3 ? Interpolation.valueOf(array.get(2).getAsString()) : Interpolation.LINEAR,
                array.size() >= 4 ? Easing.valueOf(array.get(3).getAsString()) : Easing.AUTO);
        int index = 4;
        if (previous == Interpolation.BEZIER) {
            frame.leftX = array.get(index++).getAsDouble();
            frame.leftY = array.get(index++).getAsDouble();
            index++; // Handle type affects authoring, not evaluation.
        }
        if (frame.interpolation == Interpolation.LINEAR || frame.interpolation == Interpolation.CONSTANT) {
            return frame;
        }
        if (frame.interpolation == Interpolation.BEZIER) {
            frame.rightX = array.get(index++).getAsDouble();
            frame.rightY = array.get(index++).getAsDouble();
            index++;
        }
        if (frame.interpolation == Interpolation.BACK) frame.back = array.get(index).getAsDouble();
        return frame;
    }

    record Transform(double[] values, double[] offset, int[] rotationOrder) {
        private static final Transform IDENTITY = new Transform(
                new double[] {0, 0, 0, 0, 0, 0, 1, 1, 1}, new double[3], new int[] {0, 1, 2});
    }

    private record Sequence(List<Keyframe>[] dimensions, double[] offset, int[] rotationOrder) {
        Transform transform(double millis) {
            double[] values = new double[9];
            for (int dimension = 0; dimension < dimensions.length; dimension++) {
                List<Keyframe> frames = dimensions[dimension];
                Keyframe current = null;
                Keyframe previous = null;
                int start = 0;
                int end = 0;
                for (Keyframe frame : frames) {
                    start = end;
                    end += frame.duration;
                    previous = current;
                    current = frame;
                    if (millis < end) break;
                }
                if (current == null) {
                    values[dimension] = dimension >= 6 ? 1.0D : 0.0D;
                } else if (millis >= end || current.duration == 0) {
                    values[dimension] = current.value;
                } else if (previous != null && previous.interpolation == Interpolation.CONSTANT) {
                    values[dimension] = previous.value;
                } else {
                    values[dimension] = current.interpolate(start, millis, previous);
                }
            }
            return new Transform(values, offset, rotationOrder);
        }
    }

    private static final class Keyframe {
        private final double value;
        private final int duration;
        private final Interpolation interpolation;
        private final Easing easing;
        private double leftX;
        private double leftY;
        private double rightX;
        private double rightY;
        private double back;

        private Keyframe(double value, int duration, Interpolation interpolation, Easing easing) {
            this.value = value;
            this.duration = duration;
            this.interpolation = interpolation;
            this.easing = easing;
        }

        private double interpolate(double start, double now, Keyframe previous) {
            if (previous == null) previous = new Keyframe(0.0D, 1, Interpolation.LINEAR, Easing.AUTO);
            if (Math.abs(previous.value - value) < 0.000001D) return value;
            double time = now - start;
            double change = value - previous.value;

            if (previous.interpolation == Interpolation.BEZIER) {
                double x1 = start;
                double y1 = previous.value;
                double x2 = previous.rightX;
                double y2 = previous.rightY;
                double x3 = leftX;
                double y3 = leftY;
                double x4 = start + duration;
                double y4 = value;
                double h1x = x1 - x2;
                double h1y = y1 - y2;
                double h2x = x4 - x3;
                double h2y = y4 - y3;
                double length = x4 - x1;
                if (Math.abs(h1x) > length) {
                    double factor = length / Math.abs(h1x);
                    x2 = x1 - factor * h1x;
                    y2 = y1 - factor * h1y;
                }
                if (Math.abs(h2x) > length) {
                    double factor = length / Math.abs(h2x);
                    x3 = x4 - factor * h2x;
                    y3 = y4 - factor * h2y;
                }
                return cubicBezier(y1, y2, y3, y4, findZero(now, x1, x2, x3, x4));
            }
            if (previous.interpolation == Interpolation.BACK) {
                return switch (previous.easing) {
                    case EASE_IN -> backIn(time, previous.value, change, duration, previous.back);
                    case EASE_IN_OUT -> backInOut(time, previous.value, change, duration, previous.back);
                    default -> backOut(time, previous.value, change, duration, previous.back);
                };
            }
            if (previous.interpolation == Interpolation.BOUNCE) {
                return switch (previous.easing) {
                    case EASE_IN -> bounceIn(time, previous.value, change, duration);
                    case EASE_IN_OUT -> bounceInOut(time, previous.value, change, duration);
                    default -> bounceOut(time, previous.value, change, duration);
                };
            }
            if (previous.interpolation == Interpolation.CUBIC) {
                return power(time, previous.value, change, duration, 3, previous.easing);
            }
            if (previous.interpolation == Interpolation.QUAD) {
                return power(time, previous.value, change, duration, 2, previous.easing);
            }
            if (previous.interpolation == Interpolation.SINE) {
                return switch (previous.easing) {
                    case EASE_OUT -> change * Math.sin(time / duration * Math.PI / 2.0D) + previous.value;
                    case EASE_IN_OUT -> -change / 2.0D * (Math.cos(Math.PI * time / duration) - 1.0D)
                            + previous.value;
                    default -> -change * Math.cos(time / duration * Math.PI / 2.0D)
                            + change + previous.value;
                };
            }

            double progress = time / duration;
            if (interpolation == Interpolation.SIN_UP) {
                progress = -Math.sin((progress * Math.PI + Math.PI) / 2.0D) + 1.0D;
            } else if (interpolation == Interpolation.SIN_DOWN) {
                progress = Math.sin(progress * Math.PI / 2.0D);
            } else if (interpolation == Interpolation.SIN_FULL) {
                progress = (-Math.cos(progress * Math.PI) + 1.0D) / 2.0D;
            }
            return previous.value + change * progress;
        }

        private static double power(double time, double begin, double change, double duration,
                                    int exponent, Easing easing) {
            if (easing == Easing.EASE_OUT) {
                double value = time / duration - 1.0D;
                return change * (Math.pow(value, exponent) * (exponent % 2 == 0 ? -1.0D : 1.0D) + 1.0D)
                        + begin;
            }
            if (easing == Easing.EASE_IN_OUT) {
                double value = time / (duration / 2.0D);
                if (value < 1.0D) return change / 2.0D * Math.pow(value, exponent) + begin;
                value -= 2.0D;
                double sign = exponent % 2 == 0 ? -1.0D : 1.0D;
                return sign * change / 2.0D * (Math.pow(value, exponent) - (exponent % 2 == 0 ? 2.0D : -2.0D))
                        + begin;
            }
            return change * Math.pow(time / duration, exponent) + begin;
        }

        private static double backIn(double time, double begin, double change, double duration, double overshoot) {
            time /= duration;
            return change * time * time * ((overshoot + 1.0D) * time - overshoot) + begin;
        }

        private static double backOut(double time, double begin, double change, double duration, double overshoot) {
            time = time / duration - 1.0D;
            return change * (time * time * ((overshoot + 1.0D) * time + overshoot) + 1.0D) + begin;
        }

        private static double backInOut(double time, double begin, double change,
                                        double duration, double overshoot) {
            overshoot *= 1.525D;
            time /= duration / 2.0D;
            if (time < 1.0D) {
                return change / 2.0D * (time * time * ((overshoot + 1.0D) * time - overshoot)) + begin;
            }
            time -= 2.0D;
            return change / 2.0D * (time * time * ((overshoot + 1.0D) * time + overshoot) + 2.0D) + begin;
        }

        private static double bounceOut(double time, double begin, double change, double duration) {
            time /= duration;
            if (time < 1.0D / 2.75D) {
                return change * (7.5625D * time * time) + begin;
            }
            if (time < 2.0D / 2.75D) {
                time -= 1.5D / 2.75D;
                return change * (7.5625D * time * time + 0.75D) + begin;
            }
            if (time < 2.5D / 2.75D) {
                time -= 2.25D / 2.75D;
                return change * (7.5625D * time * time + 0.9375D) + begin;
            }
            time -= 2.625D / 2.75D;
            return change * (7.5625D * time * time + 0.984375D) + begin;
        }

        private static double bounceIn(double time, double begin, double change, double duration) {
            return change - bounceOut(duration - time, 0.0D, change, duration) + begin;
        }

        private static double bounceInOut(double time, double begin, double change, double duration) {
            if (time < duration / 2.0D) {
                return bounceIn(time * 2.0D, 0.0D, change, duration) * 0.5D + begin;
            }
            return bounceOut(time * 2.0D - duration, 0.0D, change, duration) * 0.5D
                    + change * 0.5D + begin;
        }

        private static double findZero(double target, double x1, double x2, double x3, double x4) {
            return solveCubic(x1 - target, 3.0D * (x2 - x1),
                    3.0D * (x1 - 2.0D * x2 + x3), x4 - x1 + 3.0D * (x2 - x3));
        }

        private static double solveCubic(double c0, double c1, double c2, double c3) {
            if (Math.abs(c3) > 0.000001D) {
                double a = c2 / c3 / 3.0D;
                double b = c1 / c3;
                double c = c0 / c3;
                double p = b / 3.0D - a * a;
                double q = (2.0D * a * a * a - a * b + c) / 2.0D;
                double d = q * q + p * p * p;
                if (d > 0.000001D) {
                    double root = Math.sqrt(d);
                    return cubeRoot(-q + root) + cubeRoot(-q - root) - a;
                }
                if (d > -0.000001D) {
                    double root = cubeRoot(-q);
                    double result = 2.0D * root - a;
                    return inUnit(result) ? result : -root - a;
                }
                double phi = Math.acos(-q / Math.sqrt(-(p * p * p)));
                double root = Math.sqrt(-p);
                p = Math.cos(phi / 3.0D);
                q = Math.sqrt(3.0D - 3.0D * p * p);
                double result = 2.0D * root * p - a;
                if (!inUnit(result)) result = -root * (p + q) - a;
                if (!inUnit(result)) result = -root * (p - q) - a;
                return result;
            }
            double discriminant = c1 * c1 - 4.0D * c2 * c0;
            if (c2 > 0.000001D && discriminant > 0.000001D) {
                double root = Math.sqrt(discriminant);
                double result = (-c1 - root) / (2.0D * c2);
                return inUnit(result) ? result : (-c1 + root) / (2.0D * c2);
            }
            if (c2 > 0.000001D && discriminant > -0.000001D) return -c1 / (2.0D * c2);
            return c1 > 0.000001D ? -c0 / c1 : 0.0D;
        }

        private static double cubeRoot(double value) {
            return Math.abs(value) <= 0.000001D ? 0.0D : Math.copySign(Math.exp(Math.log(Math.abs(value)) / 3.0D), value);
        }

        private static boolean inUnit(double value) {
            return value >= 0.000001D && value <= 1.000001D;
        }

        private static double cubicBezier(double y1, double y2, double y3, double y4, double time) {
            double c1 = 3.0D * (y2 - y1);
            double c2 = 3.0D * (y1 - 2.0D * y2 + y3);
            double c3 = y4 - y1 + 3.0D * (y2 - y3);
            return y1 + time * c1 + time * time * c2 + time * time * time * c3;
        }
    }

    private enum Interpolation {
        CONSTANT, LINEAR, SIN_UP, SIN_DOWN, SIN_FULL, BEZIER, SINE, QUAD, CUBIC, BOUNCE, BACK
    }

    private enum Easing { AUTO, EASE_IN, EASE_OUT, EASE_IN_OUT }
}
