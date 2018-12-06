class StdLib:
    @staticmethod
    def get_sane_double(s):
        """
        Get an interpreted double for the given string. If it doesn't parse return 0.0.
        :param s: String to parse as double.
        :return: An interpreted double or 0.0 (if not convertible).
        """
        try:
            float(s)
            return float(s)
        except ValueError:
            return 0.0

    @staticmethod
    def get_sane_int(s):
        """
        Get an interpreted integer for the given string. If it doesn't parse return 0.
        :param s: String to parse as integer.
        :return: An interpreted integer or 0 (if not convertible).
        """
        try:
            int(s)
            return int(s)
        except ValueError:
            return 0

    @staticmethod
    def get_rounded_double(s):
        """
        Get a string representation of a rounded double up to 1 decimal place.
        :param s: string to be interpreted as a float
        :return: String representaton of `s` or 0.0 if unparseable.
        """
        try:
            float(s)
            return "{0:.1f}".format(float(s))
        except ValueError:
            return "0.0"

    @staticmethod
    def safe_div(x, y):
        """
        Helper method to safely divide two numbers
        :param x: Dividend
        :param y: Divisor
        :return: Division of 0 if divisior is 0.
        """
        if y == 0:
            return 0
        return x / y

    @staticmethod
    def is_empty(structure):
        if structure:
            return False
        else:
            return True
