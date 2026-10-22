let count = 0;
const label = document.getElementById('count');

function show() {
  label.textContent = count;
}

document.getElementById('plus').addEventListener('click', function () { count++; show(); });
document.getElementById('minus').addEventListener('click', function () { count--; show(); });
document.getElementById('reset').addEventListener('click', function () { count = 0; show(); });
console.log('Counter ready');
